(ns rebel-readline.service.impl.local-clojure-service
  (:require
   [rebel-readline.service.core :as core]
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.info.doc-url :as doc-url]
   [compliment.core :as compliment]
   [clojure.repl]))

;; taken from replicant
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
          ;; important to note that there could be lazy errors in this result

          ;; the strategy embraced by prepl is passing an out-fn
          ;; callback that handles formatting and message sending in
          ;; the scope of the try catch
          (merge (capture-streams) {:result result}))
        (catch Throwable t
          (merge (capture-streams)
                 (with-meta
                   {:exception (Throwable->map t)}
                   :ex t)))))))

(defn call-with-timeout [thunk timeout-ms]
  (let [thread (volatile! nil)
        ft (future-call
            (bound-fn []
              (vreset! thread (Thread/currentThread))
              (thunk)))
        timed-out (Object.)]
    (let [res (deref ft timeout-ms timed-out)]
      (if (= res timed-out)
        (do
          ;; try to gracefully stop the thread if possible
          (future-cancel ft)
          (.join @thread 100)
          (if (.isAlive @thread)
            (.stop @thread))
          {:exception (Throwable->map (Exception. "Eval timed out!"))})
        res))))

(defn safe-resolve [s]
  (some-> s
          symbol
          (-> resolve (try (catch Throwable e nil)))))

(def safe-meta (comp meta safe-resolve))

(defn resolve-meta [var-str]
  (or (safe-meta var-str)
      (when-let [ns' (some-> var-str symbol find-ns)]
        (assoc (meta ns')
               :ns var-str))))

(defn create* [options]
  (let [config-atom (atom options)]
    (reify
      clojure.lang.IDeref
      (deref [_] @config-atom)
      clojure.lang.IAtom
      (swap  [_ f] (swap! config-atom f))
      (swap  [_ f a] (swap! config-atom f a))
      (swap  [_ f a b] (swap! config-atom f a b))
      (swap  [_ f a b args] (swap! config-atom f a b args))
      (reset [_ a] (reset! config-atom a))
      core/Completions
      (-complete [_ word options]
        (if options
          (compliment/completions word options)
          (compliment/completions word)))
      core/ResolveMeta
      (-resolve-meta [_ var-str]
        (resolve-meta var-str))
      core/CurrentNs
      (-current-ns [_] (some-> *ns* str))
      core/Source
      (-source [_ var-str]
        (some->> (clojure.repl/source-fn (symbol var-str))
                 (hash-map :source)))
      core/Apropos
      (-apropos [_ var-str] (clojure.repl/apropos var-str))
      core/Document
      (-doc [self var-str]
        (when-let [{:keys [ns name]} (core/-resolve-meta self var-str)]
          (when-let [doc (compliment/documentation var-str)]
            (let [url (doc-url/url-for (str ns) (str name))]
              (cond-> {:doc doc}
                url (assoc :url url))))))
      core/Evaluation
      (-eval [self form]
        (let [res (call-with-timeout
                   #(data-eval form)
                   (get @self :eval-timeout 3000))]
          ;; set! *e outside of the thread
          (when-let [ex (some-> res :exception meta :ex)]
            (set! *e ex))
          res))
      (-eval-str [self form-str]
        (try
          (let [res (core/-read-string self form-str)]
            (if (contains? res :form)
              (core/-eval self (:form res))
              res))
          (catch Throwable e
            (set! *e e)
            {:exception (Throwable->map e)})))
      core/ReadString
      (-read-string [_ form-str]
        (when (string? form-str)
          (try
            {:form (with-in-str form-str
                     (read {:read-cond :allow} *in*))}
            (catch Throwable e
              {:exception (Throwable->map e)})))))))

(defn create
  ([] (create nil))
  ([options]
   (create* (merge core/default-config options))))
