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

(def special-forms (map name '[#_def if do quote var recur throw try catch
                               monitor-enter monitor-exit new set!]))

(def preceeding-paren "(?<=\\(\\s{0,3})")

#_(def list-start-exp #"\(\s*")

;; TODO clean up repetition here
;; easier to read if you define as regex first and let str change it to a string
;; handle meta and other reads as delimiters?

(def string-literal #"\"[^\"\\]*(?:\\.[^\"\\]*)*\"")
(def unterm-string-literal #"\"[^\"\\]*(?:\\.[^\"\\]*)*$")
(def not-delimiter-exp #"[^{}\[\]\(\)\s,\"]")

(def delimiter-exp #"[{}\[\]\(\)\s,\"]")

(def delimiter-exp-minus-quote #"[{}\[\]\(\)\s,]")

(def delimiter-or-beginning-of-line-exp #"(?<=^|[{}\[\]\(\)\s,\"])")
(def delimiter-or-end-of-line-exp #"(?=$|[{}\[\]\(\)\s,\"])")

(def delimiter-include-fslash-exp #"[{}\[\]\(\)\s,\"\/]")
(def not-delimiter-or-fslash-exp #"[^{}\[\]\(\)\s,\"\/]")

;; fix this with look-aheads and look-behind
(def meta-data-exp (str "(?:\\^(?:\\{[^\\}]*\\}|" not-delimiter-exp "+)" delimiter-exp "*)" ))

(def end-line-comment-regexp #"(;[^\n]*)\n")

(def followed-by-delimiter (str "(?=" delimiter-exp "|$)"))
(def preceeded-by-delimiter (str "(?<=" delimiter-exp "|^)"))

(def def-with-doc
  (str preceeding-paren
       "(defn|defmacro|defn-|defprotocol|defmulti)"  ;;defn
       delimiter-exp "+"
       meta-data-exp "*"
       "(" not-delimiter-exp "+)?"    ;; name
       delimiter-exp-minus-quote "*"
       "(" string-literal ")?"))     ;; docs

(def other-def
  (str preceeding-paren
       "(def" not-delimiter-exp "+)"
       delimiter-exp "+"
       meta-data-exp "*"
       "(" not-delimiter-exp "+)?"    ;; name
       ))

(def simple-def
  (str preceeding-paren
       "(def)"
       delimiter-exp "+"
       meta-data-exp "*"
       "(" not-delimiter-exp "+)?" ;; name
       delimiter-exp-minus-quote "*"
       "(" string-literal ")?" ;; docs
       ))

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

(def function-arg-exp
  (str preceeded-by-delimiter
       "(%\\d?)"
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
                                  keyword-exp "|"
                                  namespaced-symbol-exp "|"
                                  classname-exp "|"
                                  interop-call-exp "|"
                                  function-arg-exp))
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
              :keyword-colon
              :keyword-namespace
              :keyword-body
              :symbol-namespace
              :symbol-body
              :classname
              :interop-call
              :function-arg
              ))

#_(time (highlights code-str))

;; TODO tune colors later
;; TODO add backup colors when the terminal can't handle 256 colors
;; TODO make this richer to handle bold underline actually these
;; keys should just point to AttributedStyle's

;; TODO add colors for light background

(defn fg-color [color]
  (.foreground AttributedStyle/DEFAULT color))


(def highlight-colors
  {:unterm-string-literal (fg-color 1)            #_AttributedStyle/RED
   :string-literal        (.bold (fg-color 180))   #_AttributedStyle/YELLOW
   :def-doc-string        (.bold (fg-color 223))           #_AttributedStyle/YELLOW
   :def-call              (.bold (fg-color 39))            #_AttributedStyle/BLUE
   :def-varname           (.bold (fg-color 178))           #_AttributedStyle/MAGENTA
   :def-val-varname       (.bold (fg-color 85))    #_AttributedStyle/MAGENTA
   :core-macro            (.bold (fg-color 39))     #_AttributedStyle/BLUE
   :core-fn               (.bold (fg-color 178)) #_AttributedStyle/MAGENTA
   :special-form          (.bold (fg-color 39))  #_AttributedStyle/CYAN
   :keyword-colon         (.bold (fg-color 149)) #_AttributedStyle/GREEN
   :keyword-namespace     (.bold (fg-color 123)) #_AttributedStyle/CYAN
   :keyword-body          (.bold (fg-color 149)) #_AttributedStyle/GREEN
   :symbol-namespace      (.bold (fg-color 123))
   :classname             (.bold (fg-color 123))
   :function-arg          (.bold (fg-color 85))
   :interop-call          (.bold (fg-color 220))
   :line-comment          (.bold (fg-color 243))})

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

(def code-str
  "(defn parsed-line      \"asdfasdf how \\\" is {} that\"  [{:keys [cursor line word word-cursor word-index words]}]
  (assert (and cursor, line, word, word-cursor, word-index words))
  (do stuff)
  (proxy [ParsedLine] [] ;comment 1
    (pr \"hello\");; comment 2 
    (anda)
    \"asdfasdf how \\\" is {} that\"
    (.cursor [] cursor)
    (line [] line) %1
    :asdfasdf/keyword-yep :a/cow :car
    (syn-name/asdfasd [] word) 
    \"asdfasdf how \\\" is {} that\"1
    (def val-name \"doc-string\")
    (def-other val-name2 \"doc-string2\")
    (word-cursor \" [] word-cursor)
    (word-index [] word-index \\\"\\\")
    (words [] words)))  ")
