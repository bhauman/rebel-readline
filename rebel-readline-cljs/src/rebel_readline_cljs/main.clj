(ns rebel-readline-cljs.main
  (:require
   [cljs.repl :as cljs-repl]
   [cljs.repl.nashorn :as nash]
   [rebel-readline-cljs.core :refer [cljs-repl-read
                                     cljs-repl-print
                                     syntax-highlight-println]]
   [rebel-readline-cljs.service :as cljs-service]
   [rebel-readline.core :refer [line-reader
                                help-message
                                with-readline-input-stream
                                with-rebel-bindings]]
   [rebel-readline.jline-api :as api]
   [rebel-readline.service.core :as srv]))

#_(let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn []))))

(defn -main [& args]
  (let [repl-env (nash/repl-env)
        line-reader (line-reader (cljs-service/create {:repl-env repl-env}))]
    (println (help-message))
    (cljs-repl/repl
     repl-env
     :prompt (fn [])
     :print (cljs-repl-print line-reader)
     :read  (cljs-repl-read line-reader))))
