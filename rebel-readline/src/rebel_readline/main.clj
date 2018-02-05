(ns rebel-readline.main
  (:require
   [rebel-readline.core :refer [line-reader clj-repl-read with-readline-input-stream]]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   [clojure.main])
  (:gen-class))

(defn -main [& args]
  (let [reader (line-reader (local-clj-service/create))]
    (clojure.main/repl
     :prompt (fn [])
     :read (clj-repl-read reader))))
