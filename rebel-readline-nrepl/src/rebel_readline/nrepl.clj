(ns rebel-readline.nrepl
  "This is the interface for clojure tools"
  (:require [rebel-readline.nrepl.main :as main]))

(defn connect [config]
  "Connects to an nREPL server and launches a Rebel Readline REPL
   Takes the following options:

   :host string host for the nREPL server
   :port numeric port for the nREPL server"
  (main/start-repl (or config {})))
