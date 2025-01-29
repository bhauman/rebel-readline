(ns rebel-readline.nrepl.main
  (:require
   [rebel-readline.core :as core]
   [rebel-readline.clojure.main :as main]
   [rebel-readline.jline-api :as api]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   [rebel-readline.nrepl.service.nrepl :as clj-service]
   [rebel-readline.utils :refer [*debug-log*]]
   [clojure.tools.cli :as cli]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
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
        (if (#{request-prompt request-exit} input)
          input
          (do
            (api/toggle-input api/*terminal* false)
            (clj-service/eval-code
             @api/*line-reader*
             input
             (bound-fn*
              (->> identity
                   (clj-service/out-err
                    #(do (print %) (flush))
                    #(do (print %) (flush)))
                   (clj-service/value #(do (printer %) (flush))))))
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
      (when-not
          (try
            (identical? (read-eval-print) request-exit)
	    (catch Throwable e
              (repl-caught e)))
          (recur)))))

(defn start-repl* [options]
  (binding [*debug-log* true]
    (core/with-line-reader
      (clj-line-reader/create
       (clj-service/create
        (merge (when api/*line-reader* @api/*line-reader*)
               options)))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (clj-service/start-polling @api/*line-reader*)
        (.handle ^Terminal api/*terminal*
                 Terminal$Signal/INT
                 (let [line-reader api/*line-reader*]
                   (proxy [Terminal$SignalHandler] []
                     (handle [sig]
                       (clj-service/interrupt @line-reader)))))
        (println (core/help-message))
        (repl-loop)))))

;; accept symbols to make the command line easier
(s/def ::sym-or-string (s/and (s/or :sym symbol?
                                    :str string?)
                              (s/conformer #(-> % second str))))

(s/def ::tls-keys-file string?)
(s/def ::host ::sym-or-string)
(s/def ::port (s/and number? #(< 0 % 0x10000)))

(s/def ::arg-map (s/keys :req-un [::port] :opt-un [::host ::tls-keys-file]))

(defn start-repl [options]
  (if (s/valid? ::arg-map options)
    (start-repl* (s/conform ::arg-map options))
    (do
      (println "Arguments didn't pass spec")
      (println "Received these args:")
      (clojure.pprint/pprint options)
      (println "Which failed these specifications:")
      (s/explain ::arg-map options))))

;; CLI

(def cli-options
  ;; An option with a required argument
  [["-p" "--port PORT" "nREPL server Port number"
    :parse-fn parse-long
    :required "PORT"
    :default-desc "7888"
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-H" "--host HOST" "nREPL Server host"
    :default "localhost"
    :validate [string? "Must be a string"]]
   [nil "--tls-keys-file KEYFILE" "client keys file to connect via TLS"
    :default-desc "client.keys"
    :validate [string? "Must be a string"]] ;; can check if file exists here
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["RebelReadline nREPL client"
        ""
        "Usage: --host localhost --port 7888"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join "\n" errors)))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) ; help => exit OK with usage summary
      {:exit-message (usage summary) :ok? true}
      errors ; errors => exit with description of errors
      {:exit-message (error-msg errors)}
      (and (:host options) (:port options))
      {:options options}
      :else ; failed custom validation => exit with usage summary
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (start-repl options))))


