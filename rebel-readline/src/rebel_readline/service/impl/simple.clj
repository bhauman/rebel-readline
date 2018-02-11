(ns rebel-readline.service.impl.simple
  (:require
   [rebel-readline.service.core :as core]))

(defn create
  "A very simple service that you can use to get rebel readline
  working without any introspecting functionality (doc, source, appropos,
  completion, eval).
  
  It's best overide the :prompt option with a fn returns a proper
  prompt with the current namespace."
  ([] (create nil))
  ([options]
   (atom (merge
          {:prompt (fn [] "clj=> ")}
          core/default-config
          options))))
