(ns rebel-nrepl.main
  (:require
   [rebel-readline.core :as core]
   [rebel-readline.clojure.main :as main]
   [rebel-readline.jline-api :as api]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   ;[rebel-readline.clojure.service.local :as clj-service]
   [rebel-readline.nrepl.service.nrepl :as clj-service]
   ;[rebel-readline.clojure.service.simple :as simple-service]
   [rebel-readline.utils :refer [*debug-log*]]
   [clojure.main]
   [clojure.repl])
  (:import (clojure.lang LispReader$ReaderException)
           [org.jline.terminal Terminal Terminal$SignalHandler Terminal$Signal]))

(defn read-eval-print-fn [{:keys [read printer request-prompt request-exit]}]
  (fn []
    (try
      (let [input (read request-prompt request-exit)]
        (if (#{request-prompt request-exit} input)
          input
          (do
            (api/toggle-input api/*terminal* false)
            (let [res (clj-service/execute-with-client
                       @api/*line-reader*
                       {:print-value printer}
                       input)]
              (api/toggle-input api/*terminal* true)
              res))))
      #_(catch Throwable e
        
          (clojure.main/repl-caught e)))))

(defn repl-loop []
  (let [request-prompt (Object.)
        request-exit (Object.)
        read-eval-print (read-eval-print-fn
                         {:read core/repl-read-line
                          :printer main/syntax-highlight-prn*
                          :request-prompt request-prompt
                          :request-exit request-exit})]
    (try
      (clj-service/evaluate @api/*line-reader* (pr-str `(do
                                                          (require 'clojure.main)
                                                          (require 'clojure.repl))))
      #_(catch Throwable e
        (clojure.main (clojure.main/repl-caught e))))
    (loop []
      (when-not
          (try
            (identical? (read-eval-print) request-exit)
	    #_(catch Throwable e
	        (clojure.main/repl-caught e)))
          (recur)))))

;; TODO refactor this like the cljs dev repl with a "stream" and "one-line" options
(defn -main [& args]
  (println "This is the DEVELOPMENT REPL in rebel-dev.main")
  (binding [*debug-log* true]
    (core/with-line-reader
      (clj-line-reader/create
       (clj-service/create
        (when api/*line-reader* @api/*line-reader*)))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (clj-service/start-polling @api/*line-reader*)
        (.handle ^Terminal api/*terminal*
                 Terminal$Signal/INT
                 (let [line-reader api/*line-reader*]
                   (proxy [Terminal$SignalHandler] []
                     (handle [sig]
                       (tap> "HANDLED")
                       (clj-service/interrupt @line-reader)
                       (tap> "AFTER INT")))))
        (println (core/help-message))
        (repl-loop)))))


