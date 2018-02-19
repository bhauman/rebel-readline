(ns rebel-readline.main
  (:require
   [rebel-readline.core
    :refer [line-reader clj-repl-read help-message syntax-highlight-prn]]
   [rebel-readline.jline-api :as api]
   [rebel-readline.service :as srv]
   [rebel-readline.service.local-clojure :as local-clj-service]
   [clojure.main]))

(defn repl* [opts]
  (binding [api/*terminal* (or api/*terminal* (api/create-terminal))]
    (binding [api/*line-reader*
              (line-reader api/*terminal*
                           (local-clj-service/create
                            (when api/*line-reader* @api/*line-reader*)))]
      (when-let [prompt-fn (:prompt opts)]
        (swap! api/*line-reader* assoc :prompt #(with-out-str (prompt-fn))))
      (println (help-message))
      (apply
       clojure.main/repl
       (-> {:print syntax-highlight-prn
            :read (clj-repl-read)}
           (merge opts {:prompt (fn [])})
           seq
           flatten)))))

(defn repl [& opts]
  (repl* (apply hash-map opts)))

;; --------------------------------------------
;; Debug repl (Joy of Clojure)
;; --------------------------------------------

(defn contextual-eval [ctx expr]
  (eval
   `(let [~@(mapcat (fn [[k v]] [k `'~v]) ctx)]
      ~expr)))

(defmacro local-context []
  (let [symbols (keys &env)]
    (zipmap (map (fn [sym] `(quote ~sym)) symbols) symbols)))

(defmacro break []
  `(repl
    :prompt #(print "debug=> ")
    :eval (partial contextual-eval (local-context))))

(defn -main [& args]
  (binding [api/*terminal* (or api/*terminal* (api/create-terminal))]
    (repl)))
