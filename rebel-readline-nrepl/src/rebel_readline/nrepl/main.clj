(ns rebel-readline.nrepl.main
  (:require
   [rebel-readline.core :as core]
   [rebel-readline.main]
   [rebel-readline.clojure.main :as main]
   [rebel-readline.jline-api :as api]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   [rebel-readline.nrepl.service.nrepl :as clj-service]
   [rebel-readline.tools :as tools]
   [rebel-readline.nrepl.service.commands]
   [clojure.tools.cli :as cli]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [clojure.main]
   [clojure.repl])
  (:import (clojure.lang LispReader$ReaderException)
           [org.jline.terminal Terminal Terminal$SignalHandler Terminal$Signal]))

(defn repl-caught [e]
  (println "Internal REPL Error: this shouldn't happen. :repl/*e for stacktrace")
  (some-> @api/*state* :repl/error (reset! e))
  (clojure.main/repl-caught e))

(defn read-eval-print-fn [{:keys [read printer request-prompt request-exit]}]
  (fn []
    (try
      (let [input (read request-prompt request-exit)]
        (cond
          (#{request-prompt request-exit} input) input
          (not (clj-service/polling? @api/*state*)) request-exit
          :else
          (do
            (api/toggle-input api/*terminal* false)
            (clj-service/eval-code
             @api/*state*
             input
             (bound-fn*
              (cond->> identity
                (not (:background-print @api/*state*))
                (clj-service/out-err
                 #(do (print %) (flush))
                 #(do (print %) (flush)))
                true (clj-service/value #(do (printer %) (flush)))
                true (clj-service/need-input
                      (fn [_]
                        (api/toggle-input api/*terminal* true)
                        (try
                          ;; we could use a more sophisticated input reader here
                          (clj-service/send-input @api/*state* (clojure.core/read-line))
                          (catch Throwable e
                            (repl-caught e))
                          (finally
                            (api/toggle-input api/*terminal* false))))))))
            (api/toggle-input api/*terminal* true))))
      (catch Throwable e
        (repl-caught e))
      (finally
        (api/toggle-input api/*terminal* true)))))

(defn repl-loop []
  (let [request-prompt (Object.)
        request-exit (Object.)
        read-eval-print (read-eval-print-fn
                         {:read core/repl-read-line
                          :printer main/syntax-highlight-prn-unwrapped
                          :request-prompt request-prompt
                          :request-exit request-exit})]
    (try
      (clj-service/tool-eval-code
       @api/*state*
       (pr-str `(do
                  (require 'clojure.main)
                  (require 'clojure.repl))))
      (catch Throwable e
        (repl-caught e)))
    (loop []
      (when (and (clj-service/polling? @api/*state*)
                 (try
                   (not (identical? (read-eval-print) request-exit))
                   (catch Throwable e
                     (repl-caught e)
                     true)))
        (recur)))))

(defn start-repl* [options]
  (core/with-line-reader
    (clj-line-reader/create
     (clj-service/create
      (merge (when api/*state* @api/*state*)
             options)))
    (binding [*out* (api/safe-terminal-writer (api/line-reader))]
      (clj-service/register-background-printing api/*state*)
      (clj-service/start-polling @api/*state*)
      (.handle ^Terminal api/*terminal*
               Terminal$Signal/INT
               (let [state api/*state*]
                 (proxy [Terminal$SignalHandler] []
                   (handle [sig]
                     (clj-service/interrupt @state)))))
      (println (core/help-message))
      (repl-loop))))

(s/def ::sym-or-string (s/and (s/or :sym symbol?
                                    :str string?)
                              (s/conformer #(-> % second str))))

(s/def ::tls-keys-file string?)
(s/def ::host ::sym-or-string)
(s/def ::background-print boolean?)
(s/def ::port (s/and number? #(< 0 % 0x10000)))
(s/def ::port-file string?)
(s/def ::arg-map (s/merge
                  (s/keys :opt-un [::port
                                   ::port-file
                                   ::host
                                   ::tls-keys-file
                                   ::background-print])
                  :rebel-readline.tools/arg-map))

(s/def ::resolved-arg-map (s/merge
                           (s/keys :req-un [::port])
                           ::arg-map))

(def default-port-file ".nrepl-port")

(def missing-port-message
  "Must supply an nREPL port with --port PORT or :port PORT, run from a directory containing a .nrepl-port file, or supply --port-file PORTFILE / :port-file PORTFILE.")

(defn port-file-error-message [file]
  (if (.exists (io/file file))
    (format "nREPL port file %s did not contain a valid port." file)
    (format "nREPL port file %s was not found." file)))

(defn read-port-file [file]
  (let [file (io/file file)]
    (when (.exists file)
      (try
        (some-> (slurp file)
                string/trim
                not-empty
                Long/parseLong)
        (catch NumberFormatException _ nil)))))

(defn resolve-options [options]
  (let [options   (or options {})
        port-file (or (:port-file options) default-port-file)
        port      (or (:port options) (read-port-file port-file))]
    (when (and (not port) (contains? options :port-file))
      (throw (ex-info (port-file-error-message port-file)
                      {:type :rebel-readline/port-file-error
                       :port-file port-file
                       :config options})))
    (cond-> (dissoc options :port-file)
      port (assoc :port port))))

(defn conform-options [options]
  (let [resolved-options (resolve-options options)]
    (cond
      (not (:port resolved-options))
      (throw (ex-info missing-port-message
                      {:type :rebel-readline/missing-port
                       :config resolved-options}))

      (s/valid? ::resolved-arg-map resolved-options)
      (s/conform ::resolved-arg-map resolved-options)

      :else
      (throw (ex-info "Invalid configuration"
                      {:type :rebel-readline/config-spec-error
                       :config resolved-options
                       :spec ::resolved-arg-map})))))

(defn start-repl [options]
  (try
    (let [explicit-options (or options {})
          user-options (tools/user-config ::arg-map explicit-options)
          options (cond-> (conform-options (merge user-options explicit-options))
                    (not (contains? explicit-options :background-print))
                    (dissoc :background-print))]
      (start-repl*
       (merge
        clj-line-reader/default-config
        {:background-print true}
        options)))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [spec config type]} (ex-data e)]
        (case type
          (:rebel-readline/port-file-error
           :rebel-readline/missing-port)    (println (ex-message e))
          :rebel-readline/config-spec-error (tools/explain-config spec config)
          (throw e))))))

;; CLI

(def cli-options
  ;; An option with a required argument
  (vec
   (concat
    [["-p" "--port PORT" "nREPL server Port number. Defaults to .nrepl-port when present"
      :parse-fn #(Long/parseLong %)
      :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
     [nil "--port-file PORTFILE" "Path to nREPL port file. Defaults to .nrepl-port"
      :parse-fn (comp str tools/absolutize-file)]
     ["-H" "--host HOST" "nREPL Server host"
      :default "localhost"
      :validate [string? "Must be a string"]]
     [nil "--tls-keys-file KEYFILE" "client keys file to connect via TLS"
      :default-desc "client.keys"
      :parse-fn (comp str tools/absolutize-file)
      :validate [#(.exists (io/file %)) "Must be a valid path to a readable file"]]
     [nil "--no-background-print" "Disable background threads from printing"
      :id :background-print
      :update-fn (constantly false)]]
    rebel-readline.main/cli-options)))

(defn usage [options-summary]
  (->> ["rebel-readline nREPL: An enhanced client for Clojure hosted nREPL servers"
        ""
        "This is a readline enhanced REPL client intended to connect to a "
        "nREPL servers hosed by a Clojure dilect that includes the base"
        "nREPL middleware."
        ""
        "See the full README at"
        "at https://github.com/bhauman/rebel-readline-nrepl"
        ""
        "Usage: clojure -M -m rebel-readline.nrepl.main [--port 50668]"
        "If --port is omitted, --port-file is used when supplied, otherwise .nrepl-port in the current directory is used when present."
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join "\n" errors)))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options :no-defaults true)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}
      errors
      {:exit-message (error-msg errors)}
      :else
      {:options options})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (start-repl options))))

#_(-main "--port" "55" "--background-print-off")
