(ns rebel-readline.clojure.line-reader
  (:require
   [rebel-readline.commands :as commands]
   [rebel-readline.jline-api :as api :refer :all]
   [rebel-readline.jline-api.attributed-string :as astring]
   [rebel-readline.clojure.tokenizer :as tokenize]
   [rebel-readline.clojure.sexp :as sexp]
   [rebel-readline.tools :as tools :refer [color service-dispatch]]
   [rebel-readline.utils :as utils :refer [log]]
   ;; lazy-load
   #_[cljfmt.core :refer [reformat-string]]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [clojure.main])
  (:import
   [java.nio CharBuffer]
   [org.jline.keymap KeyMap]
   [org.jline.reader
    Highlighter
    Completer
    Candidate
    Parser
    Parser$ParseContext
    ParsedLine
    LineReader
    LineReader$Option
    LineReaderBuilder
    UserInterruptException
    EndOfFileException
    EOFError
    Widget]
   [org.jline.reader.impl LineReaderImpl DefaultParser BufferImpl]
   [org.jline.terminal TerminalBuilder]
   [org.jline.terminal.impl DumbTerminal]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle
    InfoCmp$Capability]))

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

(def ^:dynamic *accept-fn* nil)

(def highlight-clj-str (partial tools/highlight-str color tokenize/tag-font-lock))

;; ---------------------------------------------------------------------
;; ---------------------------------------------------------------------
;; Service Abstraction
;;
;; This readline has pluggable service behavior to allow for fetching
;; docs, etc from the appropriate environment.
;; ---------------------------------------------------------------------


;; CurrentNS
;; ----------------------------------------------

(defmulti -current-ns
  "Returns a string representation of the current ns in the current
  execution environment.

  Returns nil if it hasn't been implemented for the current service"
  service-dispatch)

;; returning nil is a sensible signal of failure here
(defmethod -current-ns :default [_])

(defn current-ns []
  (-current-ns @*line-reader*))

;; Prompt
;; ----------------------------------------------

(declare current-ns)

(defn default-prompt-fn []
  (format "%s=> "
          (or (current-ns) "clj")))

(defmethod tools/-prompt ::clojure [service]
  (default-prompt-fn))

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
  service-dispatch)

(defn default-accept-line [line-str cursor]
  (or
   (and *line-reader*
        (= "vicmd" (.getKeyMap *line-reader*)))
   (let [cursor (min (count line-str) cursor)
         x (subs line-str 0 cursor)
         tokens (tokenize/tag-sexp-traversal x)]
     (not (sexp/find-open-sexp-start tokens cursor)))))

(defmethod -accept-line :default [_ line-str cursor]
  (default-accept-line line-str cursor))

(defn accept-line [line-str cursor]
  (-accept-line @*line-reader* line-str cursor))

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
  service-dispatch)

(defmethod -complete :default [service _ _])

(defn completions
  ([word]
   (completions word nil))
  ([word options]
   (-complete @*line-reader* word options)))

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
  service-dispatch)

(defmethod -resolve-meta :default [service _])

(defn resolve-meta [wrd]
  (-resolve-meta @*line-reader* wrd))

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
  service-dispatch)

(defmethod -source :default [service _])

(defn source [wrd]
  (-source @*line-reader* wrd))

;; Apropos
;; ----------------------------------------------

(defmulti -apropos
  "Given a string returns a list of string repesentions of vars
  that match that string. This fn is already implemented on all
  the Clojure plaforms."
  service-dispatch)

(defmethod -apropos :default [service _])

(defn apropos [wrd]
  (-apropos @*line-reader* wrd))

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
  service-dispatch)

(defmethod -doc :default [service _])

(defn doc [wrd]
  (-doc @*line-reader* wrd))

;; ReadString
;; ----------------------------------------------

(defmulti -read-string
  "Given a string with that contains clojure forms this will read
  and return a map containing the first form in the string under the
  key `:form`

  Example:
  (-read-string @api/*line-reader* \"1\") => {:form 1}

  If an exception is thrown this will return a throwable map under
  the key `:exception`

  Example:
  (-read-string @api/*line-reader* \"#asdfasdfas\") => {:exception {:cause ...}}"
  service-dispatch)

(defmethod -read-string :default [service _]
  (tools/not-implemented! service "-read-string"))

(defn read-form [form-str]
  (-read-string @*line-reader* form-str))

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
  (-eval @api/*line-reader* 1) => {:result 1 :out \"\" :err \"\"}

  If an exception is thrown this will return a throwable map under
  the key `:exception`

  Example:
  (-eval @api/*line-reader* '(defn)) => {:exception {:cause ...}}

  An important thing to remember abou this eval is that it is used
  internally by the line-reader to implement various
  capabilities (line inline eval)"
  service-dispatch)

(defmethod -eval :default [service _]
  (tools/not-implemented! service "-eval"))

(defn evaluate [form]
  (-eval @*line-reader* form))

;; EvalString
;; ----------------------------------------------
;; only needed to prevent round trips for the most common
;; form of eval needed in an editor

(defmulti -eval-str
  "Just like `-eval` but takes and string and reads it before
  sending it on to `-eval`"
  service-dispatch)

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
  (-eval-str @*line-reader* form-str))

;; ----------------------------------------------------
;; ----------------------------------------------------
;; Widgets
;; ----------------------------------------------------


;; ----------------------------------------------------
;; Less Display
;; ----------------------------------------------------

(defn split-into-wrapped-lines [at-str columns]
  (mapcat (partial astring/partition-all columns)
          (astring/split-lines at-str)))

(defn window-lines [at-str-lines pos rows]
  (astring/join "\n"
                (take rows (drop pos at-str-lines))))

(defn- lines-needed [hdr columns]
  (if-let [hdr (if (fn? hdr) (hdr) hdr)]
    (count (split-into-wrapped-lines hdr columns))
    0))

(defn display-less
  ([at-str]
   (display-less at-str {}))
  ([at-str options]
   (let [{:keys [header footer]}
         (merge {:header #(astring/astr [(apply str (repeat (:cols (terminal-size)) \-))
                                         (.faint AttributedStyle/DEFAULT)])
                 :footer (astring/astr ["-- SCROLL WITH ARROW KEYS --"
                                        (color :widget/half-contrast-inverse)])}
                options)
         columns     (:cols (terminal-size))
         at-str-lines (split-into-wrapped-lines at-str columns)
         rows-needed (count at-str-lines)
         menu-keys   (get (.getKeyMaps *line-reader*)
                          LineReader/MENU)]
     (if (< (+ rows-needed
               (lines-needed (:header options) columns)
               (lines-needed (:footer options) columns))
            (- (rows-available-for-post-display) 3))
       (display-message (astring/join
                         "\n"
                         (keep identity
                               [(when-let [header (:header options)]
                                  (if (fn? header) (header) header))
                                at-str
                                (when-let [footer (:footer options)]
                                  (if (fn? footer) (footer) header))])))
       (loop [pos 0]
         (let [columns             (:cols (terminal-size))
               at-str-lines        (split-into-wrapped-lines at-str columns)
               rows-needed         (count at-str-lines)
               header              (if (fn? header) (header) header)
               footer              (if (fn? footer) (footer) footer)
               header-lines-needed (lines-needed header columns)
               footer-lines-needed (lines-needed footer columns)
               window-rows (- (rows-available-for-post-display) header-lines-needed footer-lines-needed 3)]
           (if (< 2 window-rows)
             (do
               (display-message (astring/join
                                 "\n"
                                 (keep identity
                                  [header
                                   (window-lines at-str-lines pos window-rows)
                                   footer])))
               (redisplay)
               (let [o (.readBinding *line-reader* (.getKeys ^LineReader *line-reader*) menu-keys)
                     binding-name (.name ^org.jline.reader.Reference o)]
                 (condp contains? binding-name
                   #{LineReader/UP_LINE_OR_HISTORY
                     LineReader/UP_LINE_OR_SEARCH
                     LineReader/UP_LINE}
                   (recur (max 0 (dec pos)))
                   #{LineReader/DOWN_LINE_OR_HISTORY
                     LineReader/DOWN_LINE_OR_SEARCH
                     LineReader/DOWN_LINE
                     LineReader/ACCEPT_LINE}
                   (recur (min (- rows-needed
                                  window-rows) (inc pos)))
                   ;; otherwise pushback the binding and
                   (do
                     ;; clear the post display
                     (display-message "  ")
                     ;; pushback binding
                     (when-let [s (.getLastBinding *line-reader*)]
                       (when (not= "q" s)
                         (.runMacro *line-reader* s)))))))
             ;; window is too small do nothing
             nil)))))))

;; -----------------------------------------
;; force line accept
;; -----------------------------------------

(def always-accept-line
  (create-widget
   (binding [*accept-fn* (fn [l c] true)]
     (call-widget LineReader/ACCEPT_LINE)
     true)))

;; -----------------------------------------
;; line indentation widget
;; -----------------------------------------

(defn indent-proxy-str [s cursor]
  (let [tagged-parses (tokenize/tag-sexp-traversal s)]
    ;; never indent in quotes
    ;; this is an optimization, the code should work fine without this
    (when-not (sexp/in-quote? tagged-parses cursor)
      (when-let [[delim sexp-start] (sexp/find-open-sexp-start tagged-parses cursor)]
        (let [line-start (sexp/search-for-line-start s sexp-start)]
          (str (apply str (repeat (- sexp-start line-start) \space))
               (subs s sexp-start cursor)
               "\n1" (sexp/flip-delimiter-char (first delim))))))))

(defn indent-amount [s cursor]
  (if (zero? cursor)
    0
    (if-let [prx (indent-proxy-str s cursor)]
      ;; lazy-load for faster start up
      (let [reformat-string (utils/require-resolve-var 'cljfmt.core/reformat-string)]
        (try (->>
              (reformat-string prx {:remove-trailing-whitespace? false
                                    :insert-missing-whitespace? false
                                    :remove-surrounding-whitespace? false
                                    :remove-consecutive-blank-lines? false})
              string/split-lines
              last
              sexp/count-leading-white-space)
             (catch clojure.lang.ExceptionInfo e
               (if (-> e ex-data :type (= :reader-exception))
                 (+ 2 (sexp/count-leading-white-space prx))
                 (throw e)))))
      0)))

(def indent-line-widget
  (create-widget
   (when (:indent @*line-reader*)
       (let [curs (cursor)
             s (buffer-as-string) ;; up-to-cursor better here?
             begin-of-line-pos   (sexp/search-for-line-start s (dec curs))
             leading-white-space (sexp/count-leading-white-space (subs s begin-of-line-pos))
         indent-amount       (indent-amount s begin-of-line-pos)
         cursor-in-leading-white-space? (< curs
                                           (+ leading-white-space begin-of-line-pos))]

     (cursor begin-of-line-pos)
     (delete leading-white-space)
     (write  (apply str (repeat indent-amount \space)))

     ;; rectify cursor
     (when-not cursor-in-leading-white-space?
       (cursor (+ indent-amount (- curs leading-white-space))))))
   ;; return true to re-render
   true))

(def indent-or-complete-widget
  (create-widget
    (let [curs (cursor)
          s (buffer-as-string) ;; up-to-cursor better here?
          begin-of-line-pos (sexp/search-for-line-start s (dec curs))
          leading-white-space (sexp/count-leading-white-space (subs s begin-of-line-pos))
          ;; indent-amount (#'ind/indent-amount s begin-of-line-pos)
          cursor-in-leading-white-space? (<= curs
                                             (+ leading-white-space begin-of-line-pos))]
      (if cursor-in-leading-white-space?
        (call-widget "clojure-indent-line")
        (call-widget LineReader/COMPLETE_WORD))
      true)))

;; ------------------------------------------------
;; Display argument docs on keypress functionality
;; ------------------------------------------------

(defn one-space-after-funcall-word? []
  (let [s (buffer-as-string)
        curs (cursor)
        tagged-parses (tokenize/tag-sexp-traversal s)
        [_ start _ _] (sexp/find-open-sexp-start tagged-parses curs)]
    (when start
      (when-let [[word word-start word-end _]
                 (and start (= (.charAt s start) \()
                      (sexp/funcall-word s start))]
        (and (= (+ start word-end) curs)
             word)))))

(defn name-arglist-display [meta-data]
  (let [{:keys [ns arglists name]} meta-data]
    (when (and ns name (not= ns name))
      (astring/astr
       [(str ns)   (color :widget/eldoc-namespace)]
       ["/"        (color :widget/eldoc-separator)]
       [(str name) (color :widget/eldoc-varname)]
       (when arglists [": " (color :widget/eldoc-separator)])
       (when arglists
         [(pr-str arglists) (color :widget/arglists)])))))

(defn display-argument-help-message []
  (when-let [funcall-str (one-space-after-funcall-word?)]
    (when-let [fun-meta (resolve-meta funcall-str)]
      (when (:arglists fun-meta)
        (name-arglist-display fun-meta)))))

;; TODO this ttd (time to delete) atom doesn't work really
;; need a global state and countdown solution
;; and a callback on hook on any key presses
(let [ttd-atom (atom -1)]
  (defn eldoc-self-insert-hook
    "This hooks SELF_INSERT to capture most keypresses that get echoed
  out to the terminal. We are using it here to display interactive
  behavior based on the state of the buffer at the time of a keypress."
    []
    (when (zero? @ttd-atom)
      (display-message " "))
    (when-not (neg? @ttd-atom)
      (swap! ttd-atom dec))
    ;; hook here
    ;; if prev-char is a space and the char before that is part
    ;; of a word, and that word is a fn call
    (when (:eldoc @*line-reader*)
      (when-let [message (display-argument-help-message)]
        (reset! ttd-atom 1)
        (display-message message)))))

(defn word-at-cursor []
  (sexp/word-at-position (buffer-as-string) (cursor)))

;; -------------------------------------
;; Documentation widget
;; -------------------------------------

(def document-at-point-widget
  (create-widget
   (when-let [[wrd] (word-at-cursor)]
     (when-let [doc-options (doc wrd)]
       (display-less (AttributedString. (string/trim (:doc doc-options))
                                        (color :widget/doc))
                     (when-let [url (:url doc-options)]
                       {:header (AttributedString. url (color :widget/light-anchor))}))))
   true))

;; -------------------------------------
;; Source widget
;; -------------------------------------

(defn source-at-point []
  (when-let [[wrd] (word-at-cursor)]
    (when-let [var-meta-data (resolve-meta wrd)]
      (when-let [{:keys [source]} (source wrd)]
        (when-let [name-line (name-arglist-display var-meta-data)]
          (when-not (string/blank? source)
            {:arglist-line name-line
             :source (highlight-clj-str (string/trim source))}))))))

(def source-at-point-widget
  (create-widget
   (when-let [{:keys [arglist-line source]} (source-at-point)]
     (display-less source {:header arglist-line}))
   true))

;; -------------------------------------
;; Apropos widget
;; -------------------------------------

;; most of the code below is for formatting the results into a set of columns
;; that fit the terminal

;; I experimented with creating url anchors that are supported by iTerm but
;; there seems to be a lack of support for arbitrary escape codes in Jline
(defn osc-hyper-link [url show]
  (str (char 27) "]8;;"  url (KeyMap/ctrl \G) show (char 27) "]8;;" (KeyMap/ctrl \G)))

(defn format-pair-to-width [wrd width [ns' name']]
  (let [sep (apply str (repeat (- width (count ns') (count name')) \space))
        idx (.indexOf name' wrd)]
    (astring/astr
     [(subs name' 0 idx)                   (color :widget/apropos-word)]
     [(subs name' idx (+ idx (count wrd))) (color :widget/apropos-highlight)]
     [(subs name' (+ idx (count wrd)))     (color :widget/apropos-word)]
     sep
     [ns'                                  (color :widget/apropos-namespace)])))

(defn format-column [wrd column]
  (let [max-width (apply max (map count column))]
    (map (partial format-pair-to-width wrd max-width)
         (map #(string/split % #"\/") (sort column)))))

(defn row-width [columns]
  (+ (apply + (map #(apply max (map count %))
                   columns))
     (* 3 (dec (count columns)))))

;; stats

;; TODO move to lib
(defn mean [coll]
  (if (pos? (count coll))
    (/ (apply + coll)
       (count coll))
    0))

;; TODO move to lib
;; taken from clojure cookbook
(defn standard-deviation [coll]
  (let [avg (mean coll)
        squares (for [x coll]
                  (let [x-avg (- x avg)]
                    (* x-avg x-avg)))
        total (count coll)]
    (-> (/ (apply + squares)
           (max (- total 1) 1))
        (Math/sqrt))))

;; TODO move to lib
(defn two-standards-plus-mean [coll]
  (+ (mean coll) (* 2 (standard-deviation coll))))

;; sometimes there are exceptinoally long namespaces and function
;; names these screw up formatting and are normally very disntant from
;; the thing we are looking for
(defn eliminate-long-outliers [coll]
  (let [max-len (two-standards-plus-mean (map count coll))]
    (filter #(<= (count %) max-len) coll)))

(defn find-number-of-columns [coll total-width]
  (let [suggests coll]
    (first
     (filter
      #(let [columns (partition-all (Math/ceil (/ (count suggests) %)) suggests)]
         (< (row-width columns) total-width))
      (range 10 0 -1)))))

;; the operation for finding the optimal layout is expensive
;; but this is OK as we are going to do it upon request from the
;; user and not as the user is typing
(defn divide-into-displayable-columns [coll total-width]
  (let [max-length (apply max (map count coll))
        coll (eliminate-long-outliers coll)
        cols (find-number-of-columns coll total-width)]
    (cond
      (and (= cols 1) (< (count coll) 15))
      (list coll)
      (= cols 1)
      (recur (take (int (* 0.9 (count coll))) coll) total-width)
      (> (Math/ceil (/ (count coll) cols)) 20)
      (recur (take (int (* 0.9 (count coll))) coll) total-width)
      :else
      (map sort (partition-all (Math/ceil (/ (count coll) cols)) coll)))))

(defn formatted-apropos [wrd]
  (let [suggests (sort-by (juxt count identity)
                          (map str (apropos wrd)))]
    (when-let [suggests (not-empty (take 50 suggests))]
      (let [terminal-width (:cols (terminal-size))]
        (->> (divide-into-displayable-columns suggests terminal-width)
             (map (partial format-column wrd))
             (apply map vector)
             (map #(interpose "   " %))
             (map #(apply astring/astr %))
             (interpose (apply str "\n"))
             (apply astring/astr))))))

(def apropos-at-point-widget
  (create-widget
   (when-let [[wrd] (word-at-cursor)]
     (when-let [aprs (formatted-apropos wrd)]
       (display-less aprs {:header (AttributedString.
                                    (str "Apropos for: " wrd)
                                    (.faint AttributedStyle/DEFAULT))})))
   true))

;; ------------------------------------------
;; In place eval widget
;; ------------------------------------------

(defn in-place-eval []
  (let [s (buffer-as-string)]
    (when (not (string/blank? s))
      (let [pos (cursor)
            fnw-pos (sexp/first-non-whitespace-char-backwards-from s (dec pos))
            [form-str start end typ] (sexp/sexp-or-word-ending-at-position s fnw-pos)]
        (evaluate-str form-str)))))

(defn inline-result-marker [^AttributedString at-str]
  (astring/astr ["#_=>" (color :widget/half-contrast-inverse)] " " at-str))

(defn limit-character-size [s]
  (let [{:keys [rows cols]} (terminal-size)
        max-char (int (* (- rows (count (string/split-lines (buffer-as-string))))
                         cols 0.5))]
    (if (< max-char (count s))
      (str (subs s 0 max-char) "...")
      s)))

(defn ensure-newline [s]
  (str (string/trim-newline s) "\n"))

(defn no-greater-than [limit val]
  (min limit (or val Integer/MAX_VALUE)))

(defn format-data-eval-result [{:keys [out err result printed-result exception] :as eval-result}]
  (let [[printed-result exception]
        (if (not printed-result)
          (try
            (binding [*print-length* (no-greater-than 100 *print-length*)
                      *print-level*  (no-greater-than 5 *print-level*)]
              [(pr-str result) exception])
            (catch Throwable t
              [nil (Throwable->map t)]))
          [printed-result exception])]
    (cond-> (AttributedStringBuilder.)
      exception (.styled (color :widget/error)
                         (str "=>!! "
                              (or (:cause exception)
                                  (some-> exception :via first :type))) )
      (not (string/blank? out)) (.append (ensure-newline out))
      (not (string/blank? err)) (.styled (color :widget/error) (ensure-newline err))
      (and (not exception) printed-result)
      (.append
       (inline-result-marker
        (.toAttributedString
         (highlight-clj-str printed-result)))))))

(def eval-at-point-widget
  (create-widget
   (when-let [result (in-place-eval)]
     (display-less (format-data-eval-result result)))
   true))

;; --------------------------------------------
;; Buffer position
;; --------------------------------------------

(def end-of-buffer
  (create-widget
   (cursor (.length *buffer*))
   true))

(def beginning-of-buffer
  (create-widget
   (cursor 0)
   true))

;; --------------------------------------------
;; Base Widget registration and binding helpers
;; --------------------------------------------

(defn add-all-widgets [line-reader]
  (binding [*line-reader* line-reader]
    (register-widget "clojure-indent-line"        indent-line-widget)
    (register-widget "clojure-indent-or-complete" indent-or-complete-widget)

    (register-widget "clojure-doc-at-point"       document-at-point-widget)
    (register-widget "clojure-source-at-point"    source-at-point-widget)
    (register-widget "clojure-apropos-at-point"   apropos-at-point-widget)
    (register-widget "clojure-eval-at-point"      eval-at-point-widget)

    (register-widget "clojure-force-accept-line"  always-accept-line)

    (register-widget "end-of-buffer"              end-of-buffer)
    (register-widget "beginning-of-buffer"        beginning-of-buffer)))

(defn bind-indents [km-name]
  (doto km-name
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \I))
                     "clojure-indent-line")
    (key-binding (KeyMap/ctrl \I) "clojure-indent-or-complete")))

(defn bind-clojure-widgets [km-name]
  (doto km-name
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \D)) "clojure-doc-at-point")
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \S)) "clojure-source-at-point")
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \A)) "clojure-apropos-at-point")
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \E)) "clojure-eval-at-point")
    (key-binding (str (KeyMap/ctrl \X) (KeyMap/ctrl \M)) "clojure-force-accept-line")))

(defn bind-clojure-widgets-vi-cmd [km-name]
  (doto km-name
    (key-binding (str \\ \d) "clojure-doc-at-point")
    (key-binding (str \\ \s) "clojure-source-at-point")
    (key-binding (str \\ \a) "clojure-apropos-at-point")
    (key-binding (str \\ \e) "clojure-eval-at-point")))

(defn clojure-emacs-mode [km-name]
  (doto km-name
    bind-indents
    bind-clojure-widgets
    (key-binding
     (KeyMap/key
      (.getTerminal *line-reader*)
      InfoCmp$Capability/key_end)
     "end-of-buffer")
    (key-binding
     (KeyMap/key
      (.getTerminal *line-reader*)
      InfoCmp$Capability/key_home)
     "beginning-of-buffer")))

(defn clojure-vi-insert-mode [km-name]
  (doto km-name
    clojure-emacs-mode
    (key-binding (str (KeyMap/ctrl \E)) "clojure-force-accept-line")))

(defn clojure-vi-cmd-mode [km-name]
  (doto km-name
    bind-indents
    bind-clojure-widgets
    bind-clojure-widgets-vi-cmd))

(defn add-widgets-and-bindings [line-reader]
  (binding [*line-reader* line-reader]
    (clojure-emacs-mode :emacs)
    (clojure-vi-insert-mode :viins)
    (clojure-vi-cmd-mode :vicmd)
    (swap! line-reader #(update % :self-insert-hooks (fnil conj #{}) eldoc-self-insert-hook))
    (doto line-reader
      (.setVariable LineReader/WORDCHARS "")
      add-all-widgets)))

;; ----------------------------------------------------
;; ----------------------------------------------------
;; Building a Line Reader
;; ----------------------------------------------------
;; ----------------------------------------------------


;; ---------------------------------------
;; Jline parser for Clojure
;; ---------------------------------------

;; Jline uses a parser that pulls out the words from a read line
;; it mainly uses this for word completion and line acceptance
;; I.e. is this line complete or do we need to display a secondary
;; prompt?

(defn parse-line [line cursor]
  (let [tokens (tokenize/tag-words line)
        words  (filter (comp #{:string-literal-without-quotes
                               :unterm-string-literal-without-quotes
                               :word}
                             last)
                       tokens)
        word   (first (filter
                       (fn [[_ s e]]
                         (<= s cursor e))
                       words))
        word-index (.indexOf (vec words) word)
        word-cursor (if-not word
                      0
                      (- cursor (second word)))]
    ;; TODO this can return a parsedline directly
    ;; but for now this is easier to debug
    {:word-index (or word-index -1)
     :word  (or (first word) "")
     :word-cursor word-cursor
     :tokens words
     :word-token word
     :words (map first words)
     :line line
     :cursor cursor}))

(defn parsed-line [{:keys [word-index word word-cursor words tokens line cursor] :as parse-data}]
  (proxy [ParsedLine clojure.lang.IMeta] []
    (word [] word)
    (wordIndex [] word-index)
    (wordCursor [] word-cursor)
    (words [] (java.util.LinkedList. words))
    (line [] line)
    (cursor [] cursor)
    (meta [] parse-data)))

;; this is an indent call that is specific to ACCEPT_LINE based actions
;; the functionality implemented here is indenting when you hit return on a line
(defn indent [line-reader line cursor]
  ;; you could work on the buffer here instead

  ;; TODO this key binding needs to be looked up from the macro if possible
  ;; changing the buffer here is the most stable but the logic is quite different
  ;; than the current indent action

  ;; a two key macro here adds a slight delay to the indent action I think
  (.runMacro line-reader (str (KeyMap/ctrl \X) (KeyMap/ctrl \I))))

;; a parser for jline that respects clojurisms
(defn make-parser []
  (doto
      (proxy [DefaultParser] []
        (isDelimiterChar [^CharSequence buffer pos]
          (boolean (#{\space \tab \return \newline  \, \{ \} \( \) \[ \] }
                    (.charAt buffer pos))))
        (parse [^String line cursor ^Parser$ParseContext context]
          (cond
            (= context Parser$ParseContext/ACCEPT_LINE)
            (when-not (or (and *accept-fn*
                               (*accept-fn* line cursor))
                          (accept-line line cursor))
              (indent *line-reader* line cursor)
              (throw (EOFError. -1 -1 "Unbalanced Expression" (str *ns*))))
            (= context Parser$ParseContext/COMPLETE)
            (parsed-line (parse-line line cursor))
            :else (proxy-super parse line cursor context))))
    (.setQuoteChars (char-array [\"]))))

;; ----------------------------------------
;; Jline completer for Clojure candidates
;; ----------------------------------------

;; Completion context

(defn parsed-line-word-coords [^ParsedLine parsed-line]
  (let [pos (.cursor parsed-line)
        word-cursor (.wordCursor parsed-line)
        word (.word parsed-line)
        word-start (- pos word-cursor)
        word-end (+ pos (- (count word) word-cursor))]
    [word-start word-end]))

;; TODO this has string hacks in it that wouldn't be needed
;; with better sexp parsing
(defn replace-word-with-prefix [parsed-line]
  (let [[start end] (parsed-line-word-coords parsed-line)
        [_ _ _ typ] (:word-token (meta parsed-line))
        line (.line parsed-line)]
    [(str (subs line 0 start)
         "__prefix__" (when (= typ :unterm-string-literal-without-quotes) \")
         (subs line end (count line)))
     (+ start (count "__prefix__")
        (if (#{:string-literal-without-quotes :unterm-string-literal-without-quotes}
             typ) 1 0))]))

(defn complete-context [^ParsedLine parsed-line]
  (when-let [[form-str end-of-marker] (replace-word-with-prefix parsed-line)]
    (when-let [valid-sexp (sexp/valid-sexp-from-point form-str end-of-marker)]
      (binding [*default-data-reader-fn* identity]
        (try (read-string valid-sexp)
             (catch Throwable e
               (log :complete-context e)
               nil))))))

(defn candidate [{:keys [candidate type ns]}]
  (Candidate.
   candidate ;; value
   candidate ;; display
   nil ;; group
   (cond-> nil
     type (str (first (name type)))
     ns   (str (when type " ") ns))
   nil ;; suffix
   nil ;; key
   false))

(defn command-token? [{:keys [line tokens word]} starts-with]
  (and (= 1 (count tokens))
       (.startsWith (string/triml line) starts-with)
       (.startsWith word starts-with)))

(defn find-completions [candidates prefix]
  (->> candidates
       (map str)
       (filter #(.startsWith % prefix))
       (sort-by (juxt count identity))
       (map #(hash-map :candidate % :type :repl-command))
       not-empty))

(defn repl-command-complete [{:keys [word] :as parsed-line}]
  (when (command-token? parsed-line ":r")
    (find-completions (commands/all-commands) word)))

(defn cljs-quit-complete [{:keys [word] :as parsed-line}]
  (when (command-token? parsed-line ":c")
    (find-completions [:cljs/quit] word)))

;; TODO abstract completion service here
(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (let [word (.word line)]
        (when (and
               (:completion @*line-reader*)
               (not (string/blank? word))
               (pos? (count word)))
          (let [options (let [ns' (current-ns)
                              context (complete-context line)]
                          (cond-> {}
                            ns'     (assoc :ns ns')
                            context (assoc :context context)))]
            (->>
             (or
              (repl-command-complete (meta line))
              (cljs-quit-complete (meta line))
              (completions (.word line) options))
             (map #(candidate %))
             (take 10)
             (.addAll candidates))))))))

;; ----------------------------------------
;; Jline highlighter for Clojure code
;; ----------------------------------------

(defn clojure-highlighter []
  (proxy [Highlighter] []
    (highlight [^LineReader reader ^String buffer]
      ;; this gets called on a different thread
      ;; by the window resize interrupt handler
      ;; so add this binding here
      (binding [*line-reader* reader]
        (if (:highlight @reader)
          (.toAttributedString (highlight-clj-str buffer))
          (AttributedString. buffer))))))

;; ----------------------------------------
;; Building the line reader
;; ----------------------------------------

(defn create* [terminal service & [{:keys [completer highlighter parser]}]]
  {:pre [(instance? org.jline.terminal.Terminal terminal)
         (map? service)]}
  (doto (create-line-reader terminal "Clojure Readline" service)
    (.setCompleter (or completer (clojure-completer)))
    (.setHighlighter (or highlighter (clojure-highlighter )))
    (.setParser (or parser (make-parser)))
    ;; make sure that we don't have to double escape things
    (.setOpt LineReader$Option/DISABLE_EVENT_EXPANSION)
        ;; never insert tabs
    (.unsetOpt LineReader$Option/INSERT_TAB)
    (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P #_=> ")
    ;; history
    (.setVariable LineReader/HISTORY_FILE (str (io/file ".rebel_readline_history")))
    (.setOpt LineReader$Option/HISTORY_REDUCE_BLANKS)
    (.setOpt LineReader$Option/HISTORY_IGNORE_DUPS)
    (.setOpt LineReader$Option/HISTORY_INCREMENTAL)
    add-widgets-and-bindings
    (#(binding [*line-reader* %]
        (apply-key-bindings!)
        (set-main-key-map! (get service :key-map :emacs))))))

(defn create
  "Creates a line reader takes a service as an argument.

  A service implements the multimethods found in `rebel-readline.service`

  Example:
    (create (rebel-readline.clojure.service.local/create))
  Or:
    (create (rebel-readline.clojure.service.simple/create))

  This function also takes an optional options map.

  The available options are:

  :completer - to override the clojure based completer
  :highlighter - to override the clojure based systax highlighter
  :parser - to override the clojure base word parser"
  [service & [options]]
  (create* api/*terminal* service options))
