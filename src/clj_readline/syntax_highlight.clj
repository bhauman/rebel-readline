(ns clj-readline.syntax-highlight
  (:require
   [clojure.string :as string])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]
   [java.util.regex Pattern]))

(defn match-styles [x regexp & group-styles]
  (let [m (.matcher regexp x)]
    (apply
     concat
     (take-while
      some?
      (rest
       (iterate
        #(do
           %
           (when (.find m)
             (doall
              (keep-indexed
               (fn [i style]
                 (when-let [g (.group m (inc i))]
                   [g
                    (.start m (inc i))
                    (.end m (inc i))
                    style]))
               group-styles))))
        nil))))))

(defn escape-problematic-regex-chars [s]
  (string/replace s #"(\+|\*|\?|\.)" "\\\\$1"))

(defn vars-to-reg-or-str [var-syms]
  (str "(" (string/join "|" (map  escape-problematic-regex-chars var-syms)) ")"))

;; pulling out core vars for
(defn filter-core-var-meta [f]
  (->> (clojure.repl/dir-fn 'clojure.core)
       (map (juxt (comp meta resolve) identity))
       (filter (comp f first))
       (map second)
       (map name)))

(defn core-macros []
  (filter #(not (.startsWith % "def")) ;; defs are handled elsewhere, leave for redundancy?
            (filter-core-var-meta :macro)))

(defn core-fns []
  (filter-core-var-meta #(and (:arglists %)
                              (not (:macro %)))))

(defn core-vars []
  (filter-core-var-meta #(and (not (:arglists %))
                              (not (:macro %))
                              (.startsWith (-> % :name name) "*"))))

(def special-forms (map name '[#_def if do quote var recur throw try catch
                               monitor-enter monitor-exit new set!]))

(def preceeding-paren "(?<=\\(\\s{0,3})")

(def string-literal #"(?<!\\)\"[^\"\\]*(?:\\.[^\"\\]*)*\"")
(def unterm-string-literal #"(?<!\\)\"[^\"\\]*(?:\\.[^\"\\]*)*$")

(def delimiter-exps (map str #{#"\s" #"\{" #"\}" #"\(" #"\)" #"\[" #"\]" #"," #"\"" #"\^" #"\'" #"\#"}))

(defn delims [f]
  (string/join (map str (f delimiter-exps))))

(def delimiters (delims identity))

(def delimiter-exp     (str "["  delimiters "]"))
(def not-delimiter-exp (str "[^" delimiters "]"))

(def delimiter-exp-minus-quote    (str "["  (delims (partial remove #{"\\\""})) "]"))
(def delimiter-exp-minus-meta     (str "["  (delims (partial remove #{"\\^"}))  "]"))

(def delimiter-include-fslash-exp (str "["  (delims (partial cons #"\/")) "]"))
(def not-delimiter-or-fslash-exp  (str "[^" (delims (partial cons #"\/")) "]"))
(def not-delimiter-or-period-exp  (str "[^" (delims (partial cons #"\.")) "]"))

;; fix this with look-aheads and look-behind
(def meta-data-exp (str "(?:[\\^]\\{[^\\}]+\\}|[\\^]" not-delimiter-exp "+)"))

(def meta-data-area-exp (str "(?:" delimiter-exp-minus-meta "{0,5}"
                             "(?:" meta-data-exp "))+"))

(def end-line-comment-regexp #"(;[^\n]*)\n")

(def followed-by-delimiter (str "(?=" delimiter-exp "|$)"))
(def preceeded-by-delimiter (str "(?<=" delimiter-exp "|^)"))

(def metadata-name-exp
  (str "(?:" meta-data-area-exp ")?"
       delimiter-exp "*"
       "(" not-delimiter-exp "+)?"))

(def metadata-name-docs-exp
  (str metadata-name-exp
       delimiter-exp-minus-quote "*"
       "(" string-literal ")?"))

(def def-with-doc
  (str preceeding-paren
       "(defn|defmacro|defn-|defprotocol|defmulti)"  ;;defn
       metadata-name-docs-exp)) 

(def other-def
  (str preceeding-paren
       "(def" not-delimiter-exp "+)"
       metadata-name-exp))

(def simple-def
  (str preceeding-paren
       "(def)"
       metadata-name-docs-exp))

(def core-macro-exp (str
                     preceeding-paren
                     (vars-to-reg-or-str (core-macros))
                     followed-by-delimiter))

(def special-form-exp (str preceeding-paren
                           (vars-to-reg-or-str special-forms)
                           followed-by-delimiter))

(def core-fns-exp (str preceeded-by-delimiter
                       (vars-to-reg-or-str (core-fns))
                       followed-by-delimiter))

(def core-vars-exp (str preceeded-by-delimiter
                        (vars-to-reg-or-str (core-vars))
                        followed-by-delimiter))

(def keyword-exp (str preceeded-by-delimiter
                      "(:)(?:(" not-delimiter-or-fslash-exp "+)\\/)?(" 
                      not-delimiter-or-fslash-exp "+)"))

(def namespaced-symbol-exp
  (str preceeded-by-delimiter
       "(" not-delimiter-or-fslash-exp "+)\\/(" 
       not-delimiter-or-fslash-exp "+)"))

(def classname-exp
  (str preceeded-by-delimiter
       "([A-Z]+" not-delimiter-or-fslash-exp "+)"))

(def interop-call-exp
  (str preceeded-by-delimiter
       "(\\." not-delimiter-or-fslash-exp "+)"))

(def protocol-def-name-exp
  (str preceeded-by-delimiter
       "([a-z]" not-delimiter-exp "*" "[A-Z]" not-delimiter-exp "+)"))

(def function-arg-exp
  (str preceeded-by-delimiter
       "(%\\d?)"
       followed-by-delimiter))

(def namespace-exp
  (str preceeded-by-delimiter
       "((?:" not-delimiter-or-period-exp "+\\.)+"
       not-delimiter-or-period-exp "+)"))

(def character-exp
  (str preceeded-by-delimiter
       #"(\\[^\s]|\\\w+|\\o\d+|\\u\d+)"
       followed-by-delimiter))

(def dynamic-var-exp
  (str preceeded-by-delimiter
       "(\\*" not-delimiter-exp  "+\\*)"
       followed-by-delimiter))

(defn token-tagger [syntax-str]
  (match-styles syntax-str
                (Pattern/compile (str
                                  "(" string-literal ")|"
                                  end-line-comment-regexp "|"
                                  def-with-doc "|"
                                  other-def "|"
                                  simple-def "|"
                                  core-macro-exp "|"
                                  special-form-exp "|"
                                  "(" unterm-string-literal ")|"
                                  core-fns-exp "|"
                                  core-vars-exp "|"
                                  keyword-exp "|"
                                  namespaced-symbol-exp "|"
                                  classname-exp "|"
                                  interop-call-exp "|"
                                  function-arg-exp "|"
                                  namespace-exp "|"
                                  character-exp "|"
                                  protocol-def-name-exp "|"
                                  dynamic-var-exp))
              :string-literal
              :line-comment
              :def-call
              :def-varname
              :def-doc-string
              :def-call ;-2
              :def-varname ;-2
              :def-call ;-3
              :def-val-varname
              :def-doc-string
              :core-macro
              :special-form
              :unterm-string-literal
              :core-fn
              :core-var
              :keyword-colon
              :keyword-namespace
              :keyword-body
              :symbol-namespace
              :symbol-body
              :classname
              :interop-call
              :function-arg
              :namespace
              :character
              :protocol-def-name
              :dynamic-var
              ))

#_(time (token-tagger (apply str (repeat 100 code-str))))

;; TODO add colors for light background

(defn fg-color [color]
  (.foreground AttributedStyle/DEFAULT color))

(def highlight-colors
  {:unterm-string-literal (fg-color 1)             #_AttributedStyle/RED
   :string-literal        (.bold (fg-color 180))   #_AttributedStyle/YELLOW
   :def-doc-string        (.bold (fg-color 223))   #_AttributedStyle/YELLOW
   :def-call              (.bold (fg-color 39))    #_AttributedStyle/BLUE
   :def-varname           (.bold (fg-color 178))   #_AttributedStyle/MAGENTA
   :def-val-varname       (.bold (fg-color 85))    #_AttributedStyle/MAGENTA
   :core-macro            (.bold (fg-color 39))    #_AttributedStyle/BLUE
   :core-fn               (.bold (fg-color 178))   #_AttributedStyle/MAGENTA
   :special-form          (.bold (fg-color 39))    #_AttributedStyle/CYAN
   :keyword-colon         (.bold (fg-color 149))   #_AttributedStyle/GREEN
   :keyword-namespace     (.bold (fg-color 123))   #_AttributedStyle/CYAN
   :keyword-body          (.bold (fg-color 149))   #_AttributedStyle/GREEN
   :symbol-namespace      (.bold (fg-color 123))
   :classname             (.bold (fg-color 123))
   :function-arg          (.bold (fg-color 85))
   :interop-call          (.bold (fg-color 220))
   :line-comment          (.bold (fg-color 243))
   :namespace             (.bold (fg-color 123))
   :character             (.bold (fg-color 180))
   :protocol-def-name     (.bold (fg-color 220))
   :core-var              (.bold (fg-color 167))
   :dynamic-var           (.bold (fg-color 85))})

#_(println (str (char 27) "[1;38;5;222m" ".asdfasdfasdf" (char 27 ) "0m") )

(defn style [sk]
  (highlight-colors sk))

(defn highlight-clj-str [syntax-str]
  (let [sb (AttributedStringBuilder.)]
    (loop [pos 0
           hd (token-tagger syntax-str)]
      (let [[_ start end sk] (first hd)]
        (cond
          (= (.length sb) (count syntax-str)) sb
          (= (-> hd first second) pos) ;; style active
          (do
            (if-let [st (style sk)]
              (.styled sb st (subs syntax-str start end))
              (.append sb (subs syntax-str start end)))
            (recur end (rest hd)))
          ;; TODO this could be faster if we append whole sections
          ;; instead of advancing one char at a time
          ;; but its pretty fast now 
          :else
          (do (.append sb (.charAt syntax-str pos))
              (recur (inc pos) hd)))))))

#_ (time (highlight-clj-str code-str))

#_(def code-str
  "(defn ^asdfa ^asd parsed-line      \"asdfasdf how \\\" is {} that\"  [{:keys [cursor line word word-cursor word-index words]}]
  (assert (and cursor, line, word, word-cursor, word-index words))
  (do stuff)
  (proxy [ParsedLine] [] ;comment 1
    (pr \"hello\");; comment 2 
    (anda) *ns* *asf*
    \"asdfasdf how \\\" is {} that\"
    (.cursor [] cursor)
    (line [] line) %1 \\\" \\space
    :asdfasdf/keyword-yep :a/cow :car
    (syn-name/asdfasd [] word) 
    \"asdfasdf how \\\" is {} that\"1
    (def val-name \"doc-string\")
    (def-other val-name2 \"doc-string2\")
    (word-cursor \" [] word-cursor)
    (word-index [] word-index \\\"\\\")
    (words [] words)))  ")
