(ns rebel-readline.clojure.main
  (:require
   [rebel-readline.core :as core]
   [rebel-readline.clojure.line-reader :as clj-line-reader]
   [rebel-readline.jline-api :as api]
   [rebel-readline.tools :as tools]
   [rebel-readline.clojure.service.local :as clj-service]
   [clojure.repl :as repl]
   [clojure.main]))

(defn- handle-sigint-form
  []
  `(let [thread# (Thread/currentThread)]
     (repl/set-break-handler! (fn [_signal#] (.stop thread#)))))

(defn syntax-highlight-prn-unwrapped
  [x]
  (try
      (println (api/->ansi (clj-line-reader/highlight-clj-str x)))
      (catch java.lang.StackOverflowError e
        (println x))))

(defn syntax-highlight-prn*
  "Print a syntax highlighted clojure string.

  This printer respects the current color settings set in the
  service.

  The `rebel-readline.jline-api/*line-reader*` and
  `rebel-readline.jline-api/*service*` dynamic vars have to be set for
  this to work.

  See `rebel-readline.main` for an example of how this function is normally used"
  [x]
  (binding [*out* (.. api/*line-reader* getTerminal writer)]
    (syntax-highlight-prn-unwrapped x)))

(defn syntax-highlight-prn
  "Print a syntax highlighted clojure value.

  This printer respects the current color settings set in the
  service.

  The `rebel-readline.jline-api/*line-reader*` and
  `rebel-readline.jline-api/*service*` dynamic vars have to be set for
  this to work.

  See `rebel-readline.main` for an example of how this function is normally used"
  [x]
  (syntax-highlight-prn* (pr-str x)))

;; this is intended to only be used with clojure repls
(def create-repl-read
  "A drop in replacement for clojure.main/repl-read, since a readline
  can return multiple Clojure forms this function is stateful and
  buffers the forms and returns the next form on subsequent reads.

  This function is a constructor that takes a line-reader and returns
  a function that can replace `clojure.main/repl-read`.

  Example Usage:

  (clojure.main/repl
   :prompt (fn []) ;; prompt is handled by line-reader
   :read (clj-repl-read
           (line-reader
             (rebel-readline.clojure.service.local/create))))

  Or catch a bad terminal error and fall back to clojure.main/repl-read:

  (clojure.main/repl
   :prompt (fn [])
   :read (try
          (clj-repl-read
           (line-reader
             (rebel-readline.clojure.service.local/create)))
          (catch clojure.lang.ExceptionInfo e
             (if (-> e ex-data :type (= :rebel-readline.jline-api/bad-terminal))
                (do (println (.getMessage e))
                    clojure.main/repl-read)
                (throw e)))))"
  (core/create-buffered-repl-reader-fn
   (fn [s] (clojure.lang.LineNumberingPushbackReader.
            (java.io.StringReader. s)))
   core/has-remaining?
   clojure.main/repl-read))

(let [clj-repl clojure.main/repl]
  (defn repl* [{:keys [:rebel-readline/config] :as opts}]
    (let [opts (dissoc opts :rebel-readline/config)
          ;; would prefer not to have this here
          final-config (merge clj-line-reader/default-config
                              (tools/user-config ::tools/arg-map config)
                              (when api/*line-reader* @api/*line-reader*)
                              config)]
      (core/with-line-reader
          (clj-line-reader/create
           (clj-service/create final-config))
        ;; still debating about wether to include the following line in
        ;; `with-line-reader`. I am thinking that taking over out should
        ;; be opt in when using the lib taking over out provides
        ;; guarantees by Jline that Ascii commands are processed correctly
        ;; on different platforms, this particular writer also protects
        ;; the prompt from corruption by ensuring a newline on flush and
        ;; forcing a prompt to redisplay if the output is printed while
        ;; the readline editor is enguaged
          (binding [*out* (api/safe-terminal-writer api/*line-reader*)]
            (when-let [prompt-fn (:prompt opts)]
              (swap! api/*line-reader* assoc :prompt prompt-fn))
            (println (core/help-message))
            (apply
             clj-repl
             (-> {:print syntax-highlight-prn
                  :eval (fn [form]
                          (eval `(do ~(handle-sigint-form) ~form)))
                  :read (create-repl-read)}
                 (merge opts {:prompt (fn [])})
                 seq
                 flatten)))))))

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
  (core/ensure-terminal (repl)))
