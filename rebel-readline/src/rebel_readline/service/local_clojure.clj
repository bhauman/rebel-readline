(ns rebel-readline.service.local-clojure
  (:require
   [rebel-readline.service :as service]
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.info.doc-url :as doc-url]
   [compliment.core :as compliment]
   [clojure.repl]))

;; taken from replicant
;; https://github.com/puredanger/replicant/blobcl/master/src/replicant/util.clj
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
                   {:ex t})))))))

(defn call-with-timeout [thunk timeout-ms]
  (let [prom (promise)
        thread (Thread. (bound-fn [] (deliver prom (thunk))))
        timed-out (Object.)]
    (.start thread)
    (let [res (deref prom timeout-ms timed-out)]
      (if (= res timed-out)
        (do
          (.join thread 100)
          (if (.isAlive thread)
            (.stop thread))
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

(defmethod service/-resolve-meta ::service [_ var-str]
  (resolve-meta var-str))

(defmethod service/-complete ::service [_ word options]
  (if options
    (compliment/completions word options)
    (compliment/completions word)))

(defmethod service/-current-ns ::service [_]
  (some-> *ns* str))

(defmethod service/-source ::service [_ var-str]
  (some->> (clojure.repl/source-fn (symbol var-str))
           (hash-map :source)))

(defmethod service/-apropos ::service [_ var-str]
  (clojure.repl/apropos var-str))

(defmethod service/-doc ::service [self var-str]
  (when-let [{:keys [ns name]} (service/-resolve-meta self var-str)]
    (when-let [doc (compliment/documentation var-str)]
      (let [url (doc-url/url-for (str ns) (str name))]
        (cond-> {:doc doc}
          url (assoc :url url))))))

(defmethod service/-eval ::service [self form]
  (let [res (call-with-timeout
             #(data-eval form)
             (get self :eval-timeout 3000))]
    ;; set! *e outside of the thread
    (when-let [ex (some-> res :exception meta :ex)]
      (set! *e ex))
    res))

(defmethod service/-read-string ::service [self form-str]
  (when (string? form-str)
    (try
      {:form (with-in-str form-str
               (read {:read-cond :allow} *in*))}
      (catch Throwable e
        {:exception (Throwable->map e)}))))

(defn create
  ([] (create nil))
  ([options]
   (atom (merge service/default-config options {::service/type ::service}))))
