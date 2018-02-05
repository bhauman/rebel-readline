(ns rebel-readline.service.core
  (:require
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.tools.read-forms :as forms])
  (:import
   [org.jline.utils AttributedStyle]))

(def ^:dynamic *service* nil)

(defprotocol Config
  (-get-config [_])
  (-set-config! [_ v]))

(defprotocol Completions
  (-complete [_ prefix options]
    "Takes a word prefix and an options map}
     
    The options map can contain
    `:ns`      - the current namespace the completion is occuring in
    `:context` - a sexp form that contains a marker '__prefix__
       replacing the given prefix in teh expression where it is being
       completed. i.e. '(list __prefix__ 1 2)

    Returns a list of candidates of the form

    {:candidate \"alength\"
     :ns \"clojure.core\"
     :type :function}"))

(defprotocol CurrentNs
  (-current-ns [_]
    "Returns a string representation of the current ns in the current
     execution environment."))

(defprotocol ResolveMeta
  (-resolve-meta [_ var-str]
    "Currently this finds and returns the meta data for the given
    string currently we are using the :ns, :name, :doc and :arglist
    meta data that is found on both vars, namespaces

    This function should return the standard or enhanced meta
    information that is afor a given \"word\" that and editor can
    focus on.

    `(resolve (symbol var-str))`

    This function shouldn't throw errors but catch them and return nil
    if the var doesn't exist."))

;; TODO Maybe better to have a :file :line-start :line-end and a :url
(defprotocol Source
  (-source [_ var-str]
    "Given a string that represents a var Returns a map with source
     information for the var or nil if no source is found.
     
     A required :source key which will hold a string of the source code 
     for the given var.

     An optional :url key which will hold a url to the source code in
     the context of the original file or potentially some other helpful url.
   
     DESIGN NOTE the :url isn't currently used

     Example result for `(-source \"some?\")`:
       
       {:source \"(defn ^boolean some? [x] \\n(not (nil? x)))\"
        :url \"https://github.com[...]main/cljs/cljs/core.cljs#L243-L245\" }"))

(defprotocol Apropos
  (-apropos [_ var-str]
    "Given a string returns a list of string repesentions of vars 
    that match that string. This fn is already implemented on all 
    the Clojure plaforms."))

(defprotocol Document
  (-doc [_ var-str]
    "Given a string that represents a var, returns a map with
    documentation information for the named var or nil if no
    documentation is found.
     
    A required :doc key which will hold a string of the documentation 
    for the given var.

    An optional :url key which will hold a url to the online
    documentation for the given var."))

(defprotocol AcceptLine
  (-accept-line [_ line cursor]
    "Takes a string that is represents the current contents of a
     readline buffer and an integer position into that readline that
     represents the current position of the cursor.

     Returns a boolean indicating wether the line is complete and
     should be accepted as input.

     A service is not required to implement this fn, they would do
     this to override the default accept line behavior"))

(defprotocol ReadString
  (-read-string [_ str-form]
    "Given a string with that contains clojure forms this will read
    and return a map containing the first form in the string under the 
    key `:form`

    Example:
    (-read-string *service* \"1\") => {:form 1}
    
    If an exception is thrown this will return a throwable map under
    the key `:exception` 

    Example:
    (-read-string *service* \"#asdfasdfas\") => {:exception {:cause ...}}"))

(defprotocol Evaluation
  (-eval [_ form]
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
    capabilities (line inline eval)")
  
  (-eval-str [_ form-str]
    "Just like `-eval` but takes and string and reads it before
    sending it on to `-eval`"))

(declare current-ns)

(defn default-prompt-fn []
  (format "%s=> " (current-ns)))

(def default-config
  {:completion true
   :indent true
   :eldoc true
   :highlight true
   :redirect-output true
   :color-theme :dark-screen-theme})

(defn config [] (-get-config *service*))

(defn apply-to-config [f & args]
  (when-let [res (apply f (config) args)]
    (-set-config! *service* res)))

(defn current-ns []
  (when (satisfies? CurrentNs *service*)
    (-current-ns *service*)))

(defn completions
  ([word]
   (completions word nil))
  ([word options]
   (when (satisfies? Completions *service*)
     (-complete *service* word options))))

(defn apropos [wrd]
  (when (satisfies? Apropos *service*)
    (-apropos *service* wrd)))

(defn doc [wrd]
  (when (satisfies? Document *service*)
    (-doc *service* wrd)))

(defn source [wrd]
  (when (satisfies? Source *service*)
    (-source *service* wrd)))

(defn resolve-meta [wrd]
  (when (satisfies? ResolveMeta *service*)
    (-resolve-meta *service* wrd)))

(defn accept-line [line-str cursor]
  (if (satisfies? AcceptLine *service*)
    (-accept-line *service* line-str cursor)
    (forms/default-accept-line line-str cursor)))

(defn read-form [form-str]
  (when (satisfies? ReadString *service*)
    (-read-string *service* form-str)))

(defn evaluate [form]
  (when (satisfies? Evaluation *service*)
    (-eval *service* form)))

(defn evaluate-str [form-str]
  (when (satisfies? Evaluation *service*)
   (-eval-str *service* form-str)))

(defn color [sk]
  (->
   (get (config) :color-theme)
   colors/color-themes 
   (get sk AttributedStyle/DEFAULT)))

(defn resolve-fn? [f]
  (cond
    (nil? f) nil
    (fn? f) f
    (or (string? f) (symbol? f))
    (resolve (symbol f))
    :else nil))

(defn prompt []
  ((or (resolve-fn? (:prompt (config)))
       default-prompt-fn)))

