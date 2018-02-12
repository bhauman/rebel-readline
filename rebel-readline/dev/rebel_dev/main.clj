(ns rebel-dev.main
  (:require
   [rebel-readline.core :refer [line-reader clj-repl-read with-readline-input-stream
                                help-message]]
   [rebel-readline.service.core :as srv]
   [rebel-readline.jline-api :as api]
   [rebel-readline.utils :refer [*debug-log*]]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   [rebel-readline.service.impl.simple :as simple-service]
   [clojure.main])
  (:gen-class))

(defn -main [& args]
  (prn :repl-dev-main)
  (let [reader (line-reader
                #_(simple-service/create)
                (local-clj-service/create))]
    (binding [api/*line-reader* (:line-reader reader)
              srv/*service* (:service reader)
              *debug-log* true]
      (println (help-message))
      (clojure.main/repl
       :prompt (fn [])
       :read (clj-repl-read reader)))))

#_(defn -main [& args]
  (let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn [])))))
