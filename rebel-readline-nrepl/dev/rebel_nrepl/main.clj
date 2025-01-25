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
   [clojure.main])
  (:import (clojure.lang LispReader$ReaderException)))

(defn read-eval-print-fn [{:keys [read printer request-prompt request-exit]}]
  (fn []
    (try
      (let [input (read request-prompt request-exit)]
        (if (#{request-prompt request-exit} input)
          input
          (clj-service/execute-with-client @api/*line-reader*
                                           {:print-value printer}
                                           input)))
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
          (try (identical? (read-eval-print) request-exit)
	       #_(catch Throwable e
	         (clojure.main/repl-caught e)))
        (recur)))))


#_(loop []
         (when-not
           (try (identical? (read-eval-print) request-exit)
	    (catch Throwable e
	     (caught e)
	     (set! *e e)
	     nil))
           (when (need-prompt)
             (prompt)
             (flush))
           (recur)))

;; TODO refactor this like the cljs dev repl with a "stream" and "one-line" options
(defn -main [& args]
  (println "This is the DEVELOPMENT REPL in rebel-dev.main")
  (binding [*debug-log* true]
    (core/with-line-reader
      (clj-line-reader/create
       (clj-service/create
        (when api/*line-reader* @api/*line-reader*)))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (clj-service/start-polling (when api/*line-reader* @api/*line-reader*))
        (println (core/help-message))
        #_((clj-repl-read) (Object.) (Object.))
        (repl-loop)
        #_(clojure.main/repl
           :prompt (fn [])
           :eval (fn [x]
                   (let [{:keys [result out err] :as res} (clj-line-reader/evaluate (pr-str x))]
                     (when out (print out))
                     (when err
                       (binding [*out* *err*]
                         (print err)))
                     1
                     #_(if result
                         (read-string result)
                         nil)))
           :print main/syntax-highlight-prn
           :read (main/create-repl-read))))))

#_(defn -main [& args]
  (let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn [])))))
