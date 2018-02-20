(ns rebel-dev.main
  (:require
   [rebel-readline.core :as core]
   [rebel-readline.clojure.main :as main]
   [rebel-readline.jline-api :as api]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   [rebel-readline.clojure.service.local :as clj-service]
   [rebel-readline.clojure.service.simple :as simple-service]
   [rebel-readline.utils :refer [*debug-log*]]
   [clojure.main]))

;; TODO refactor this like the cljs dev repl with a "stream" and "one-line" options
(defn -main [& args]
  (println "This is the DEVELOPMENT REPL in rebel-dev.main")
  (binding [*debug-log* true]
    (core/with-line-reader
      (clj-line-reader/create
       (clj-service/create
        #_{:key-map :viins}))
      (println (core/help-message))
      #_((clj-repl-read) (Object.) (Object.))
      (clojure.main/repl
       :prompt (fn [])
       :print main/syntax-highlight-prn
       :read (main/create-repl-read)))))

#_(defn -main [& args]
  (let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn [])))))
