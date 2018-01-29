(ns clj-readline.service.impl.local-clojure-service
  (:require
   [clj-readline.service.core :as core]
   [clj-readline.tools.colors :as colors]
   [compliment.core :as compliment]
   [clojure.repl]))

;; takent from replicant
;; https://github.com/puredanger/replicant/blob/master/src/replicant/util.clj
(defn data-eval
  [form]
  (let [out-writer (java.io.StringWriter.)
        err-writer (java.io.StringWriter.)
        capture-streams (fn []
                          (.flush *out*)
                          (.flush *err*)
                          {:out (.toString out-writer)
                           :err (.toString err-writer)})]
    (binding [*out* (java.io.BufferedWriter. out-writer)
              *err* (java.io.BufferedWriter. err-writer)]
      (try
        (let [result (eval form)]
          (merge (capture-streams) {:result result}))
        (catch Throwable t
          (set! *e t)
          (merge (capture-streams) {:exception (Throwable->map t)}))))))

(defn safe-resolve [s]
  (some-> s
          symbol
          (-> resolve (try (catch Throwable e nil)))))

(def safe-meta (comp meta safe-resolve))

(defn create* [options]
  (let [config-atom (atom options)]
    (reify
      core/Config
      (-get-config [_] @config-atom)
      (-set-config! [_ v] (reset! config-atom v))
      core/Completions
      (-complete [_ word options]
        (if options
          (compliment/completions word options)
          (compliment/completions word)))
      core/ResolveVarMeta
      (-resolve-var-meta [_ var-str]
        (safe-meta var-str))
      core/ResolveNsMeta
      (-resolve-ns-meta [_ ns-str]
        (when-let [ns' (find-ns (symbol ns-str))]
          (assoc (meta ns')
                 :name ns-str)))
      core/CurrentNs
      (-current-ns [_] (some-> *ns* str))
      core/Source
      (-source [_ var-str] (clojure.repl/source-fn (symbol var-str)))
      core/Apropos
      (-apropos [_ var-str] (clojure.repl/apropos var-str))
      core/Document
      (-doc [_ var-str]
        (compliment/documentation var-str))
      core/Evaluation
      (-eval [_ form]
        (data-eval form))
      (-eval-str [_ form-str]
        (try
          (data-eval (read-string form-str))
          (catch Throwable e
            (set! *e e)
            {:exception (Throwable->map e)})))
      core/ReadString
      (-read-string [_ form-str] (read-string form-str)))))



(defn create
  ([] (create nil))
  ([options]
   (create* (merge core/default-config options))))

#_(core/-get-config (create {}))








