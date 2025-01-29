(ns rebel-readline.nrepl.service.commands
  (:require [rebel-readline.commands :refer [command command-doc]]
            [rebel-readline.jline-api :as api]))

(defmethod command-doc :repl/toggle-background-print [_]
  "Toggle wether to continue the printing the output from backgrounded threads")

(defmethod command :repl/toggle-background-print [_]
  (swap! api/*line-reader* update :background-print #(not %))
  (if (:background-print @api/*line-reader*)
    (println "Background printing on!")
    (println "Background printing off!")))
