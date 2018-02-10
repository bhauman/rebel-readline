(ns rebel-readline.service.impl.simple
  (:require
   [rebel-readline.service.core :as core]))

;; The simplest possible service that you can use to get
;; rebel readline working

;; overide the :prompt option with a fn returns a proper
;; prompt with the current namespace

(defn create* [options]
  (let [config-atom
        (atom (merge
               {:prompt (fn [] "clj=> ")}
               options))]
    (reify
      clojure.lang.IDeref
      (deref [_] @config-atom)
      clojure.lang.IAtom
      (swap  [_ f] (swap! config-atom f))
      (swap  [_ f a] (swap! config-atom f a))
      (swap  [_ f a b] (swap! config-atom f a b))
      (swap  [_ f a b args] (swap! config-atom f a b args))
      (reset [_ a] (reset! config-atom a)))))

(defn create
  ([] (create nil))
  ([options]
   (create* (merge core/default-config options))))
