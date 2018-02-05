(ns rebel-readline-cljs.main
  (:require
   [cljs.repl :as cljs-repl]
   [cljs.repl.nashorn :as nash]
   [rebel-readline-cljs.service :as cljs-service]   
   [rebel-readline.core :refer [line-reader with-readline-input-stream]]
   [rebel-readline-cljs.core :refer [cljs-repl-read]])
  (:gen-class))

#_(let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn []))))

(defn -main [& args]
  (let [repl-env (nash/repl-env)
        line-reader (line-reader (cljs-service/create {:repl-env repl-env}))]
    (cljs-repl/repl
     repl-env
     :prompt (fn [])
     :read (cljs-repl-read line-reader))))
