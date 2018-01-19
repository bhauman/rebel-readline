(ns clj-readline.syntax-highlight
  (:require
   [clojure.string :as string]
   [clj-readline.parsing.tokenizer :as tokenize])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

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
           hd (tokenize/tag-syntax syntax-str)]
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
