(ns rebel-readline.main
  (:require
   [rebel-readline.core
    :refer [line-reader clj-repl-read with-readline-input-stream help-message]]
   [rebel-readline.service.core :as srv]
   [rebel-readline.tools.syntax-highlight :as highlight]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   [clojure.main])
  (:gen-class))

(defn -main [& args]
  (let [reader (line-reader (local-clj-service/create))]
    (println (help-message))
    (clojure.main/repl
     :prompt (fn [])
     :print (fn [x]
              (binding [srv/*service* (:service reader)]
                (println (.toAnsi (highlight/highlight-clj-str (pr-str x))))))
     :read (clj-repl-read reader))))
