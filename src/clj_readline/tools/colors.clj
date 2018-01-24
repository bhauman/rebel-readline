(ns clj-readline.tools.colors
  (:import
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

(defn fg-color [color]
  (.foreground AttributedStyle/DEFAULT color))

(def dark-screen-theme
  {:unterm-string-literal (.bold (fg-color 180))   #_AttributedStyle/YELLOW
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
   :dynamic-var           (.bold (fg-color 85))

   ;; system widget colors
   :eldoc-namespace (.faint (fg-color 123))
   :eldoc-separator (fg-color 243)
   :eldoc-varname   (.faint (fg-color 178))
   :eldoc-arglists  (fg-color 243)

   :doc             (fg-color 222)
   :light-anchor    (.faint (fg-color 39))

   :apropos-word       AttributedStyle/DEFAULT
   :apropos-highlight  (fg-color 45)
   :apropos-namespace  (.faint (.foreground AttributedStyle/DEFAULT 243))
   })

;; TODO fix these
(def light-screen-theme
  (assoc dark-screen-theme
         :symbol-namespace  (.bold (fg-color 28))
         :keyword-namespace (.bold (fg-color 28))
         :namespace         (.bold (fg-color 28))
         :classname         (.bold (fg-color 28))
         :eldoc-namespace   (.faint (fg-color 28))

         :keyword-colon     (.bold (fg-color 31))
         :keyword-body      (.bold (fg-color 31))

         :eldoc-varname     (.faint (fg-color 21))
         :def-varname       (.bold  (fg-color 21))
         :core-macro        (.bold  (fg-color 21))
         
         
         :def-val-varname   (.bold (fg-color 130))
         :function-arg      (.bold (fg-color 130))
         :dynamic-var       (.bold (fg-color 130))
         
         :def-call          (.bold (fg-color 127))
         :core-fn           (.bold (fg-color 127))
         :special-form      (.bold (fg-color 127))
         :string-literal        (.bold (fg-color 127))
         :unterm-string-literal (.bold (fg-color 127))
         :character         (.bold (fg-color 127))
         
         :interop-call      (.bold (fg-color 97))
         :protocol-def-name (.bold (fg-color 97))
         :def-doc-string    (.bold (fg-color 132))

         :line-comment      (.bold (fg-color 247))

         :doc             (.bold (fg-color 127))
         :light-anchor    (.underline (.faint (fg-color 26)))
         
         :apropos-word       AttributedStyle/DEFAULT
         :apropos-highlight  (fg-color 27)
         :apropos-namespace  (.faint (.foreground AttributedStyle/DEFAULT 243))

         ))

#_(println (str (char 27) "[1;38;5;222m" ".asdfasdfasdf" (char 27 ) "0m") )

#_(.toAnsi (AttributedString. "hey" AttributedStyle/DEFAULT #_(.bold (fg-color 85))))

