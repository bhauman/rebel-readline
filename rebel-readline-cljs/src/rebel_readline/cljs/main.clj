(ns rebel-readline.cljs.main
  (:require
   [cljs.repl.nashorn :as nash]
   [rebel-readline.cljs.repl :as cljs-repl]
   [rebel-readline.core :as core]))

;; TODO need ot bring this in line with cljs.main
(defn -main [& args]
  (let [repl-env (nash/repl-env)]
    (cljs-repl/repl repl-env)))
