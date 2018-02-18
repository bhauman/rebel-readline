(ns rebel-readline.service
  (:require
   [rebel-readline.jline-api :as api]
   [rebel-readline.clojure.tokenizer :as tokenize]
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.tools.sexp :as sexp]
   [rebel-readline.utils :as utils]
   [rebel-readline.jline-api :as api])
  (:import
   [org.jline.utils AttributedStyle]))

(def ^:dynamic *service* nil)

;; Why not just have an Eval multimethod and be done???

;; Firstly this is a readline and being able to eval shouldn't be a
;; hard requirement when the local clojure process can provide a lot
;; of functionality (think completion) in the absense of eval

;; It gives flexibility for situations where folks want to handle some
;; of these tasks in the local process

;; This is inspired by clojurescripts special needs, where some things
;; are much more elegantly handled in the Clojure process that is
;; hosting the repl. It's nice to not have to depend on the browser be
;; an identity function to ack a value that is local.


;; ----------------------------------------------
;; Default Configuration
;; ----------------------------------------------

(def default-config
  {:completion true
   :indent true
   :eldoc true
   :highlight true
   :redirect-output true
   :key-map :emacs
   :color-theme
   (if (= :light (utils/terminal-background-color?))
     :light-screen-theme
     :dark-screen-theme)})

;; ----------------------------------------------
;; utilities
;; ----------------------------------------------

(defn resolve-fn? [f]
  (cond
    (fn? f) f
    (or (string? f) (symbol? f))
    (resolve (symbol f))
    :else nil))

(defn not-implemented! [service fn-name]
  (throw (ex-info (format "The %s service does not implement the %s function."
                          (pr-str (::type service))
                          fn-name)
                  {})))

;; ----------------------------------------------
;; multi-methods that have default behavior
;; ----------------------------------------------


;; CurrentNS
;; ----------------------------------------------

(defmulti -current-ns
  "Returns a string representation of the current ns in the current
  execution environment.

  Returns nil if it hasn't been implemented for the current service"
  (fn [a & args] (::type a)))

;; returning nil is a sensible signal of failure here
(defmethod -current-ns :default [_])

(defn current-ns []
  (-current-ns @*service*))

;; Prompt
;; ----------------------------------------------

(defmulti -prompt
  "returns a repl prompt string"
  (fn [a & args] (::type a)))

(declare current-ns)

(defn default-prompt-fn []
  (format "%s=> "
          (or (current-ns) "clj")))

(defmethod -prompt :default [service]
  (default-prompt-fn))

;; TODO this is a good start
(defn prompt []
  (if-let [f (resolve-fn? (:prompt @*service*))]
    (f)
    (-prompt @*service*)))

;; AcceptLine
;; ----------------------------------------------

(defmulti -accept-line
  "Takes a string that is represents the current contents of a
  readline buffer and an integer position into that readline that
  represents the current position of the cursor.

  Returns a boolean indicating wether the line is complete and
  should be accepted as input.

  A service is not required to implement this fn, they would do
  this to override the default accept line behavior"
  (fn [a & args] (::type a)))

(defn default-accept-line [line-str cursor]
  (or
   (and api/*line-reader*
        (= "vicmd" (.getKeyMap api/*line-reader*)))
   (let [cursor (min (count line-str) cursor)
         x (subs line-str 0 cursor)
         tokens (tokenize/tag-sexp-traversal x)]
     (not (sexp/find-open-sexp-start tokens cursor)))))

(defmethod -accept-line :default [_ line-str cursor]
  (default-accept-line line-str cursor))

(defn accept-line [line-str cursor]
  (-accept-line @*service* line-str cursor))

;; Completion
;; ----------------------------------------------

(defmulti -complete
  "Takes a word prefix and an options map}

    The options map can contain
    `:ns`      - the current namespace the completion is occuring in
    `:context` - a sexp form that contains a marker '__prefix__
       replacing the given prefix in teh expression where it is being
       completed. i.e. '(list __prefix__ 1 2)

    Returns a list of candidates of the form

    {:candidate \"alength\"
     :ns \"clojure.core\"
     :type :function}"
  (fn [a & args] (::type a)))

(defmethod -complete :default [service _ _])

(defn completions
  ([word]
   (completions word nil))
  ([word options]
   (-complete @*service* word options)))

;; ResolveMeta
;; ----------------------------------------------

(defmulti -resolve-meta
  "Currently this finds and returns the meta data for the given
  string currently we are using the :ns, :name, :doc and :arglist
  meta data that is found on both vars, namespaces

  This function should return the standard or enhanced meta
  information that is afor a given \"word\" that and editor can
  focus on.

  `(resolve (symbol var-str))`

  This function shouldn't throw errors but catch them and return nil
  if the var doesn't exist."
  (fn [a & args] (::type a)))

(defmethod -resolve-meta :default [service _])

(defn resolve-meta [wrd]
  (-resolve-meta @*service* wrd))

;; ----------------------------------------------
;; multi-methods that have to be defined or they
;; throw an exception
;; ----------------------------------------------

;; Source
;; ----------------------------------------------

;; TODO Maybe better to have a :file :line-start :line-end and a :url
(defmulti -source
  "Given a string that represents a var Returns a map with source
  information for the var or nil if no source is found.

  A required :source key which will hold a string of the source code
  for the given var.

  An optional :url key which will hold a url to the source code in
  the context of the original file or potentially some other helpful url.

  DESIGN NOTE the :url isn't currently used

  Example result for `(-source service \"some?\")`:

    {:source \"(defn ^boolean some? [x] \\n(not (nil? x)))\"
     :url \"https://github.com[...]main/cljs/cljs/core.cljs#L243-L245\" }"
  (fn [a & args] (::type a)))

(defmethod -source :default [service _])

(defn source [wrd]
  (-source @*service* wrd))

;; Apropos
;; ----------------------------------------------

(defmulti -apropos
  "Given a string returns a list of string repesentions of vars
  that match that string. This fn is already implemented on all
  the Clojure plaforms."
  (fn [a & args] (::type a)))

(defmethod -apropos :default [service _])

(defn apropos [wrd]
  (-apropos @*service* wrd))

;; Doc
;; ----------------------------------------------

(defmulti -doc
  "Given a string that represents a var, returns a map with
  documentation information for the named var or nil if no
  documentation is found.

  A required :doc key which will hold a string of the documentation
  for the given var.

  An optional :url key which will hold a url to the online
  documentation for the given var."
  (fn [a & args] (::type a)))

(defmethod -doc :default [service _])

(defn doc [wrd]
  (-doc @*service* wrd))

;; ReadString
;; ----------------------------------------------

(defmulti -read-string
  "Given a string with that contains clojure forms this will read
  and return a map containing the first form in the string under the
  key `:form`

  Example:
  (-read-string *service* \"1\") => {:form 1}

  If an exception is thrown this will return a throwable map under
  the key `:exception`

  Example:
  (-read-string *service* \"#asdfasdfas\") => {:exception {:cause ...}}"
  (fn [a & args] (::type a)))

(defmethod -read-string :default [service _]
  (not-implemented! service "-read-string"))

(defn read-form [form-str]
  (-read-string @*service* form-str))

;; Eval
;; ----------------------------------------------

(defmulti -eval
  "Given a clojure form this will evaluate that form and return a
  map of the outcome.

  The returned map will contain a `:result` key with a clj form that
  represents the result of it will contain a `:printed-result` key
  if the form can only be returned as a printed value.

  The returned map will also contain `:out` and `:err` keys
  containing any captured output that occured during the evaluation
  of the form.

  Example:
  (-eval *service* 1) => {:result 1 :out \"\" :err \"\"}

  If an exception is thrown this will return a throwable map under
  the key `:exception`

  Example:
  (-eval *service* '(defn)) => {:exception {:cause ...}}

  An important thing to remember abou this eval is that it is used
  internally by the line-reader to implement various
  capabilities (line inline eval)"
  (fn [a & args] (::type a)))

(defmethod -eval :default [service _]
  (not-implemented! service "-eval"))

(defn evaluate [form]
  (-eval @*service* form))

;; EvalString
;; ----------------------------------------------
;; only needed to prevent round trips for the most common
;; form of eval needed in an editor

(defmulti -eval-str
  "Just like `-eval` but takes and string and reads it before
  sending it on to `-eval`"
  (fn [a & args] (::type a)))

(defmethod -eval-str :default [service form-str]
  (try
    (let [res (-read-string service form-str)]
      (if (contains? res :form)
        (-eval service (:form res))
        res))
    (catch Throwable e
      (set! *e e)
      {:exception (Throwable->map e)})))

(defn evaluate-str [form-str]
  (-eval-str @*service* form-str))

;; Color
;; ----------------------------------------------

(defn color [sk]
  (->
   (get @*service* :color-theme)
   colors/color-themes
   (get sk AttributedStyle/DEFAULT)))

;; ----------------------------------------------
;; Lifecyle
;; ----------------------------------------------

;; Initialize
;; ----------------------------------------------

(defmulti -init
  "This function is called when we create a new service or when we
  switch services."
  (fn [a & args] (::type a)))

(defmethod -init :default [_])

;; this is where we bring all the state into alignment
;; with the passed in config
(defn pre-init []
  (api/set-main-key-map! (get @*service* :key-map :emacs)))

(defn init []
  (pre-init)
  (-init @*service*))
