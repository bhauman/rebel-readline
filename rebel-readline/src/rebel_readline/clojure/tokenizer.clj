(ns rebel-readline.clojure.tokenizer
  (:require
   [clojure.string :as string]
   [clojure.repl])
  (:import
   [java.util.regex Pattern]))

;; we need to quickly tokenize partial clojure expressions
;; for syntax highlighting and sexp text manipulation

;; this is a regexp based tokenizer for clojure that meets these needs well enough

(defn tag-matches [x regexp & group-styles]
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
                 (when style
                   (when-let [g (.group m (inc i))]
                     [g
                      (.start m (inc i))
                      (.end m (inc i))
                      style])))
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

(def string-literal-without-quotes #"(?<!\\)\"([^\"\\]*(?:\\.[^\"\\]*)*)\"")
(def unterm-string-literal-without-quotes #"(?<!\\)\"([^\"\\]*(?:\\.[^\"\\]*)*)$")

(def string-literal-capture-quotes-body #"(?<!\\)((\")([^\"\\]*(?:\\.[^\"\\]*)*)(\"))")
(def unterm-string-literal-capture-quote-body #"(?<!\\)((\")([^\"\\]*(?:\\.[^\"\\]*)*))$")

(def delimiter-exps (map str #{#"\s" #"\{" #"\}" #"\(" #"\)" #"\[" #"\]"
                               #"," #"\"" #"\^" #"\'" #"\#" #"\@"}))

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

(def end-line-comment-regexp #"(;[^\n]*)(?=\n|$)")

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
       "(defn\\-|defn|defmacro|defprotocol|defmulti)"  ;;defn
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
  (str #_preceeded-by-delimiter
       #"(\\[^\s]|\\\w+|\\o\d+|\\u\d+)"
       followed-by-delimiter))

(def dynamic-var-exp
  (str preceeded-by-delimiter
       "(\\*" not-delimiter-exp  "+\\*)"
       followed-by-delimiter))

(def syntax-token-tagging-rexp
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
                    dynamic-var-exp)))

(defn tag-syntax [syntax-str]
  (tag-matches syntax-str
               syntax-token-tagging-rexp
               :string-literal
               :line-comment
               :def-call
               :def-varname
               :def-doc-string
               :def-call
               :def-varname
               :def-call
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
               :dynamic-var))

(defn tag-font-lock [syntax-str]
  (tag-matches syntax-str
               syntax-token-tagging-rexp
               :font-lock/string        ;; :string-literal
               :font-lock/comment       ;; :line-comment
               :font-lock/core-form     ;; :def-call
               :font-lock/function-name ;; :def-varname
               :font-lock/doc           ;; :def-doc-string
               :font-lock/core-form     ;; :def-call
               :font-lock/function-name ;; :def-varname
               :font-lock/core-form     ;; :def-call
               :font-lock/variable-name ;; :def-val-varname
               :font-lock/doc           ;; :def-doc-string
               :font-lock/core-form     ;; :core-macro
               :font-lock/core-form     ;; :special-form
               :font-lock/string        ;; :unterm-string-literal
               :font-lock/function-name ;; :core-fn
               :font-lock/builtin       ;; :core-var
               :font-lock/constant      ;; :keyword-colon
               :font-lock/type          ;; :keyword-namespace
               :font-lock/constant      ;; :keyword-body
               :font-lock/type          ;; :symbol-namespace
               :symbol-body
               :font-lock/type          ;; :classname
               :font-lock/foreign       ;; :interop-call
               :font-lock/variable-name ;; :function-arg
               :font-lock/type          ;; :namespace
               :font-lock/string        ;; :character
               :font-lock/foreign       ;; :protocol-def-name
               :font-lock/variable-name ;; :dynamic-var
               ))

(def non-interp-regexp
  (Pattern/compile
   (str
    end-line-comment-regexp "|"
    "(" string-literal ")|"
    "(" unterm-string-literal ")|"
    character-exp )))

(defn tag-non-interp [code-str]
  (tag-matches code-str
               non-interp-regexp
               :end-line-comment
               :string-literal
               :unterm-string-literal
               :character))

(def sexp-traversal-rexp
  (Pattern/compile
   (str
    end-line-comment-regexp "|"
    string-literal-capture-quotes-body "|"
    unterm-string-literal-capture-quote-body "|"
    character-exp "|"
    #"(\()" "|" ; open paren
    #"(\))" "|" ; close paren
    #"(\{)" "|" ; open brace
    #"(\})" "|" ; close brace
    #"(\[)" "|" ; open bracket
    #"(\])"     ; close bracket
    )))

(defn tag-sexp-traversal [code-str]
  (tag-matches code-str
               sexp-traversal-rexp
               :end-line-comment
               nil
               :open-quote
               :string-literal-body
               :close-quote
               nil
               :open-quote
               :unterm-string-literal-body
               :character
               :open-paren
               :close-paren
               :open-brace
               :close-brace
               :open-bracket
               :close-bracket))

(def non-interp-word-rexp
  (Pattern/compile
      (str
       end-line-comment-regexp "|"
       string-literal-without-quotes "|"
       unterm-string-literal-without-quotes "|"
       character-exp "|"
       "(" not-delimiter-exp "+)")))

(defn tag-words
  "This tokenizes a given string into
     :end-of-line-comments
     :string-literals-without-quotes
     :unterm-string-literal
     :charater s
  and the remaining
     :word s

  This allows us to opertate on words that are outside of
strings, comments and characters. "
  [line]
  (tag-matches line
               non-interp-word-rexp
               :end-line-comment
               :string-literal-without-quotes
               :unterm-string-literal-without-quotes
               :character
               :word))
