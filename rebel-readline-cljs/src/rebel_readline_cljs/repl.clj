(ns rebel-readline-cljs.repl
  (:require
   [cljs.repl]
   [rebel-readline-cljs.core :refer [cljs-repl-read syntax-highlight-println]]
   [rebel-readline-cljs.service :as cljs-service]
   [rebel-readline.core :refer [line-reader help-message with-rebel-bindings]]
   [rebel-readline.jline-api :as api]
   [rebel-readline.service :as srv]))

;; with an attempt to support nesting

(defn cljs-repl* [repl-env opts]
  (let [service (cljs-service/create
                 (merge
                  (when srv/*service* @srv/*service*)
                  {:repl-env repl-env}))
        line-reader
        (if api/*line-reader*
          {:line-reader api/*line-reader*
           :service service}
          (line-reader service))]
    (when-let [prompt-fn (:prompt opts)]
      (swap!
       service assoc :prompt
       (fn [] (with-out-str (prompt-fn)))))
    (with-rebel-bindings line-reader
      (println (help-message))
      (cljs.repl/repl*
       repl-env
       (merge
        {:print syntax-highlight-println
         :read  (cljs-repl-read line-reader)}
        opts
        {:prompt (fn [])})))))

(defn cljs-repl
  "This is just a proxy for the cljs.repl/repl that does the required
  bookeeping to engauge the Rebel line-reader or to easily nest a cljs
  repl inside of an existing repl that is using the line-reader
  already."
  [repl-env & opts]
  (cljs-repl* repl-env (apply hash-map opts)))
