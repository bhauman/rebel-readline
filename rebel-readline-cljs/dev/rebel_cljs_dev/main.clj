(ns rebel-cljs-dev.main
  (:require
   [cljs.repl :as cljs-repl]
   [cljs.repl.nashorn :as nash]
   [rebel-readline-cljs.service :as cljs-service]
   [rebel-readline.core :refer [line-reader
                                help-message
                                with-readline-input-stream
                                with-rebel-bindings]]
   [rebel-readline.jline-api :as api]
   [rebel-readline.service.core :as srv]
   [rebel-readline.utils :as utils]
   [rebel-readline-cljs.core :refer [cljs-repl-read syntax-highlight-println]]))

#_(let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn []))))

(defn -main [& args]
  (let [repl-env (nash/repl-env)
        service (cljs-service/create {:repl-env repl-env})]
    (println "This is the DEVELOPMENT REPL in rebel-cljs-dev.main")
    (println (help-message))
    (binding [utils/*debug-log* true]
      (if (= "stream" (first args))
        (do
          (println "[[Input stream line reader]]")
          (with-readline-input-stream service
            (cljs-repl/repl
             repl-env
             :prompt (fn [])
             :print syntax-highlight-println)))
        (let [line-reader (line-reader service)]
          (with-rebel-bindings line-reader
            (cljs-repl/repl
             repl-env
             :prompt (fn [])
             :read (cljs-repl-read line-reader)
             :print syntax-highlight-println)))))))
