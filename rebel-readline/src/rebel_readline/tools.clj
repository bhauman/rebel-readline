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
  {:font-lock/string         (.bold (fg-color 180))
   :font-lock/comment        (.bold (fg-color 243))
   :font-lock/doc            (.bold (fg-color 223))
   :font-lock/core-form      (.bold (fg-color 39))
   :font-lock/function-name  (.bold (fg-color 178))
   :font-lock/variable-name  (.bold (fg-color 85))
   :font-lock/constant       (.bold (fg-color 149))
   :font-lock/type           (.bold (fg-color 123))
   :font-lock/foreign        (.bold (fg-color 220))
   :font-lock/builtin        (.bold (fg-color 167))

   :widget/half-contrast     (fg-color 243)
   :widget/half-contrast-inverse (.inverse (fg-color 243))

   ;; system widget colors
   :widget/eldoc-namespace   (.faint (fg-color 123))
   :widget/eldoc-varname     (.faint (fg-color 178))
   :widget/eldoc-separator   (fg-color 243)
   :widget/arglists          (fg-color 243)

   :widget/doc               (fg-color 222)
   :widget/anchor            (fg-color 39)
   :widget/light-anchor      (.faint (fg-color 39))

   :widget/apropos-word      AttributedStyle/DEFAULT
   :widget/apropos-highlight (fg-color 45)
   :widget/apropos-namespace (.faint (fg-color 243))

   :widget/warning           AttributedStyle/DEFAULT
   :widget/error             (fg-color 196)

   })

(register-color-theme! :dark-screen-theme dark-screen-theme)

;; TODO fix these
(def light-screen-theme
  (assoc dark-screen-theme
         :font-lock/type            (.bold (fg-color 28))
         :font-lock/constant        (.bold (fg-color 31))
         :font-lock/function-name   (.bold  (fg-color 21))
         ;:font-lock/core-form      (.bold  (fg-color 21))
         :font-lock/variable-name   (.bold (fg-color 130))
         :font-lock/core-form       (.bold (fg-color 127))
         :font-lock/string          (.bold (fg-color 127))
         :font-lock/foreign         (.bold (fg-color 97))
         :font-lock/doc             (.bold (fg-color 132))
         :font-lock/comment         (.bold (fg-color 247))

         :widget/eldoc-namespace    (fg-color 28)
         :widget/eldoc-varname      (fg-color 21)
         ;:widget/eldoc-separator   (fg-color 243)
         ;:widget/arglists          (fg-color 243)

         :widget/doc                (fg-color 238)
         :widget/light-anchor       (.underline (.faint (fg-color 26)))

         :widget/apropos-word       AttributedStyle/DEFAULT
         :widget/apropos-highlight  (fg-color 27)
         :widget/apropos-namespace  (fg-color 243)))

(register-color-theme! :light-screen-theme light-screen-theme)
