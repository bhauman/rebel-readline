(ns rebel-readline.nrepl
  "This is the interface for clojure tools"
  (:require [rebel-readline.nrepl.main :as main]))

(defn connect [config]
  (main/start-repl (or config {})))
