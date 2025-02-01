(ns rebel-readline.tool
  (:require
   [clojure.spec.alpha :as s]
   [clojure.java.io :as io]
   [rebel-readline.tools :as tools]
   [rebel-readline.clojure.main :as main]))

(defn repl [options]
  (if (tools/valid-config? options)
    (main/repl* {:rebel-readline/config options})
    (tools/explain-config options)))
