(ns rebel-readline-cljs.core
  (:require
   [cljs.repl]
   [rebel-readline.core :as rebel]
   [clojure.tools.reader :as r]
   [clojure.tools.reader.reader-types :as rtypes]))

(defn has-remaining?
  "Takes a clojure.tools.reader.reader-types/SourceLoggingPushbackReader
   and returns true if there is another character in the stream.
   i.e not the end of the readers stream."
  [pbr]
  (boolean
   (when-let [x (rtypes/read-char pbr)]
     (rtypes/unread pbr x)
     true)))

(def cljs-repl-read
 "A drop in replacement for cljs.repl/repl-read, since a readline
  can return multiple Clojure forms this function is stateful and
  buffers the forms and returns the next form on subsequent reads.

  This function is a constructor that takes a line-reader and returns
  a function that can replace `cljs.repl/repl-read`.

  Example Usage:

  (let [repl-env (nash/repl-env)]
    (cljs-repl/repl
     repl-env
     :prompt (fn [])
     :read (cljs-repl-read
             (rebel-readline.core/line-reader
               (rebel-readline-cljs.service/create {:repl-env repl-env}))])))

  Or catch a bad terminal error and fall back to cljs.repl/repl-read:

  (let [repl-env (nash/repl-env)]
    (cljs-repl/repl
     repl-env
     :prompt (fn [])
     :read (try
             (cljs-repl-read
               (rebel-readline.core/line-reader
                 (rebel-readline-cljs.service/create {:repl-env repl-env})))
             (catch clojure.lang.ExceptionInfo e
               (if (-> e ex-data :type (= :rebel-readline.line-reader/bad-terminal))
                 (do (println (.getMessage e))
                     cljs.repl/repl-read)
                 (throw e))))"
  (rebel/create-buffered-repl-reader-fn
   (fn [s] (rtypes/source-logging-push-back-reader
            (java.io.StringReader. s)))
   has-remaining?
   cljs.repl/repl-read))
