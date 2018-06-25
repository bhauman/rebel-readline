(ns rebel-readline.cljs.repl
  (:require
   [cljs.repl]
   [clojure.tools.reader :as r]
   [clojure.tools.reader.reader-types :as rtypes]
   [rebel-readline.core :as rebel]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   [rebel-readline.cljs.service.local :as cljs-service]
   [rebel-readline.jline-api :as api])
  (:import
   [org.jline.utils OSUtils]))

(defn has-remaining?
  "Takes a clojure.tools.reader.reader-types/SourceLoggingPushbackReader
   and returns true if there is another character in the stream.
   i.e not the end of the readers stream."
  [pbr]
  (boolean
   (when-let [x (rtypes/read-char pbr)]
     (rtypes/unread pbr x)
     true)))

(def create-repl-read
 "Creates a drop in replacement for cljs.repl/repl-read, since a
  readline can return multiple Clojure forms this function is stateful
  and buffers the forms and returns the next form on subsequent reads.

  This function is a constructor that takes a line-reader and returns
  a function that can replace `cljs.repl/repl-read`.

  Example Usage:

  (let [repl-env (nash/repl-env)]
    (cljs-repl/repl
     repl-env
     :prompt (fn [])
     :read (cljs-repl-read
             (rebel-readline.core/line-reader
               (rebel-readline-cljs.service/create {:repl-env repl-env}))])))"
  (rebel/create-buffered-repl-reader-fn
   (fn [s] (rtypes/source-logging-push-back-reader
            (java.io.StringReader. s)))
   has-remaining?
   cljs.repl/repl-read))

(defn syntax-highlight-println
  "Print a syntax highlighted clojure value.

  This printer respects the current color settings set in the
  service.

  The `rebel-readline.jline-api/*line-reader*` and
  `rebel-readline.jline-api/*service*` dynamic vars have to be set for
  this to work.

  See `rebel-readline-cljs.main` for an example of how this function is normally used"
  [x]
  (binding [*out* (.. api/*line-reader* getTerminal writer)]
    (try
      (println (api/->ansi (clj-line-reader/highlight-clj-str (or x ""))))
      (catch java.lang.StackOverflowError e
        (println (or x ""))))))

;; enable evil alter-var-root
(let [cljs-repl* cljs.repl/repl*]
  (defn repl* [repl-env opts]
    (rebel/with-line-reader
      (clj-line-reader/create
       (cljs-service/create (assoc
                             (when api/*line-reader*
                               @api/*line-reader*)
                             :repl-env repl-env)))
      (when-let [prompt-fn (:prompt opts)]
        (swap! api/*line-reader* assoc :prompt prompt-fn))
      (println (rebel/help-message))
      (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
        (cljs-repl*
         repl-env
         (merge
          {:print syntax-highlight-println
           :read (create-repl-read)}
          opts
          {:prompt (fn [])}))))))

(defn repl [repl-env & opts]
  (repl* repl-env (apply hash-map opts)))
