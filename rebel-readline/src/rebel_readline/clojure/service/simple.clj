(ns rebel-readline.clojure.service.simple
  (:require
   [rebel-readline.clojure.line-reader :as lr]
   [rebel-readline.tools :as tools]))

(defn create
  "A very simple service that you can use to get rebel readline
  working without any introspecting functionality (doc, source, appropos,
  completion, eval).

  It's best overide the :prompt option with a fn returns a proper
  prompt with the current namespace."
  ([] (create nil))
  ([options]
   (merge
    {:prompt (fn [] (println "clj=> "))}
    lr/default-config
    (tools/user-config)
    options)))
