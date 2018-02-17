(ns rebel-readline.main
  (:require
   [rebel-readline.core
    :refer [line-reader clj-repl-read help-message syntax-highlight-prn]]
   [rebel-readline.jline-api :as api]
   [rebel-readline.service :as srv]
   [rebel-readline.service.impl.local-clojure-service :as local-clj-service]
   [clojure.main]))

(defn -main [& args]
  (let [reader (line-reader (local-clj-service/create))]
    (println (help-message))
    (binding [api/*line-reader* (:line-reader reader)
              srv/*service* (:service reader)]
      (clojure.main/repl
       :prompt (fn [])
       :print syntax-highlight-prn
       :read (clj-repl-read reader)))))
