(ns rebel-dev.main
  (:require
   [rebel-readline.core :refer [line-reader clj-repl-read with-readline-input-stream]]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   [clojure.main])
  (:gen-class))

(defn -main [& args]
  (prn :repl-dev-main)
  (let [reader (line-reader (local-clj-service/create))]
    (clojure.main/repl
     :prompt (fn [])
     :read (clj-repl-read reader))))

#_(defn -main [& args]
  (let [repl-env (nash/repl-env)]
    (with-readline-input-stream (cljs-service/create {:repl-env repl-env})
      (cljs-repl/repl repl-env :prompt (fn [])))))
