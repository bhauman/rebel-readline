(ns rebel-readline.cljs.main
  (:require
   [cljs.repl.node :as node]
   [rebel-readline.cljs.repl :as cljs-repl]
   [rebel-readline.core :as core]))

;; TODO need to bring this in line with cljs.main
(defn -main [& args]
  (let [repl-env (node/repl-env)]
    (cljs-repl/repl repl-env)))
