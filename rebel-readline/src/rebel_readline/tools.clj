(ns rebel-readline.tools
  (:require
   [rebel-readline.jline-api :as api])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedStyle]))

;; ----------------------------------------------
;; Extra Abilities
;; ----------------------------------------------

;; Color
;; ----------------------------------------------

(def color-themes {})

(defn register-color-theme! [ky color-map]
  (assert (keyword? ky))
  (assert (map? color-map))
  (alter-var-root #'color-themes assoc ky color-map))

(defn fg-color [color]
  (.foreground AttributedStyle/DEFAULT color))

(defn color [sk]
  (->
   (get @api/*line-reader* :color-theme)
   color-themes
   (get sk AttributedStyle/DEFAULT)))

;; String Highlighting
;; ----------------------------------------------

(defn highlight-tokens [color-fn tokens syntax-str]
  (let [sb (AttributedStringBuilder.)]
    (loop [pos 0
           hd tokens]
      (let [[_ start end sk] (first hd)]
        (cond
          (= (.length sb) (count syntax-str)) sb
          (= (-> hd first second) pos) ;; style active
          (do
            (if-let [st (color-fn sk)]
              (.styled sb st (subs syntax-str start end))
              (.append sb (subs syntax-str start end)))
            (recur end (rest hd)))
          ;; TODO this could be faster if we append whole sections
          ;; instead of advancing one char at a time
          ;; but its pretty fast now
          :else
          (do (.append sb (.charAt syntax-str pos))
              (recur (inc pos) hd)))))))

(defn highlight-str [color-fn tokenizer-fn syntax-str]
  (highlight-tokens color-fn (tokenizer-fn syntax-str) syntax-str))

;; Baseline services
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

(defn service-dispatch [a & args] (:rebel-readline.service/type a))

;; Prompt
;; ----------------------------------------------

(defmulti -prompt
  "returns a read-line prompt string"
  service-dispatch)

(defmethod -prompt :default [_] "")

(defn prompt []
  (if-let [f (resolve-fn? (:prompt @api/*line-reader*))]
    (f)
    (-prompt @api/*line-reader*)))

;; Initial Themes

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

   :error (fg-color 196)
   :inline-display-marker (.inverse (fg-color 243))
   :less-help-message     (.inverse (fg-color 243))
   })

(register-color-theme! :dark-screen-theme dark-screen-theme)

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

(register-color-theme! :light-screen-theme light-screen-theme)
