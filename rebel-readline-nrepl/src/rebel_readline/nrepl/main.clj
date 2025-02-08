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
  (some-> @api/*line-reader* :repl/error (reset! e))
  (clojure.main/repl-caught e))

(defn read-eval-print-fn [{:keys [read printer request-prompt request-exit]}]
  (fn []
    (try
      (let [input (read request-prompt request-exit)]
        (cond
          (#{request-prompt request-exit} input) input
          (not (clj-service/polling? @api/*line-reader*)) request-exit
          :else
          (do
            (api/toggle-input api/*terminal* false)
            (clj-service/eval-code
             @api/*line-reader*
             input
             (bound-fn*
              (cond->> identity
                (not (:background-print @api/*line-reader*))
                (clj-service/out-err
                 #(do (print %) (flush))
                 #(do (print %) (flush)))
                true (clj-service/value #(do (printer %) (flush)))
                true (clj-service/need-input
                      (fn [_]
                        (api/toggle-input api/*terminal* true)
                        (try
                          ;; we could use a more sophisticated input reader here
                          (clj-service/send-input @api/*line-reader* (clojure.core/read-line))
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
       @api/*line-reader*
       (pr-str `(do
                  (require 'clojure.main)
                  (require 'clojure.repl))))
      (catch Throwable e
        (repl-caught e)))
    (loop []
      (when (and (clj-service/polling? @api/*line-reader*)
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
        (merge (when api/*line-reader* @api/*line-reader*)
               options)))
    (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
      (clj-service/register-background-printing api/*line-reader*)
      (clj-service/start-polling @api/*line-reader*)
      (.handle ^Terminal api/*terminal*
               Terminal$Signal/INT
               (let [line-reader api/*line-reader*]
                 (proxy [Terminal$SignalHandler] []
                   (handle [sig]
                     (clj-service/interrupt @line-reader)))))
      (println (core/help-message))
      (repl-loop))))

(s/def ::sym-or-string (s/and (s/or :sym symbol?
                                    :str string?)
                              (s/conformer #(-> % second str))))

(s/def ::tls-keys-file string?)
(s/def ::host ::sym-or-string)
(s/def ::background-print boolean?)
(s/def ::port (s/and number? #(< 0 % 0x10000)))
(s/def ::arg-map (s/merge
                  (s/keys :req-un [::port]
                          :opt-un
                          [::host
                           ::tls-keys-file
                           ::background-print])
                  :rebel-readline.tools/arg-map))

(defn start-repl [options]
  (try
    (start-repl*
     (merge
      clj-line-reader/default-config
      (tools/user-config ::arg-map options)
      {:background-print true}
      (s/conform ::arg-map options)))
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [spec config] :as err} (ex-data e)]
        (if (-> err :type (= :rebel-readline/config-spec-error))
          (tools/explain-config spec config)
          (throw e))))))

;; CLI

(def cli-options
  ;; An option with a required argument
  (vec
   (concat
    [["-p" "--port PORT" "nREPL server Port number"
      :parse-fn #(Long/parseLong %)
      :required "PORT"
      :default-desc "7888"
      :missing "Must supply a -p PORT to connect to"
      :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
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
        "Usage: clojure -M -m rebel-readline.nrepl.main --port 50668"
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

