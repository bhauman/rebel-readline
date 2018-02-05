(ns rebel-readline.main
  (:require
   [rebel-readline.core :refer [line-reader clj-repl-read with-readline-input-stream]]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   
   [rebel-readline.service.impl.cljs-service :as cljs-service]   
   [cljs.repl.nashorn :as nash]
   [cljs.repl :as cljs-repl]

   [clojure.main])
  (:gen-class))

(defn new-repl []
  (with-readline-input-stream (local-clj-service/create)
    (clojure.main/repl :prompt (fn []))))

(defn -main [& args]
  (let [reader (line-reader (local-clj-service/create))]
    (clojure.main/repl
     :prompt (fn [])
     :read (clj-repl-read reader)))
  
  #_(let [repl-env (nash/repl-env)]
    (lr/with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn []))))
  
  )
