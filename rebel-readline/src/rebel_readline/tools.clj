(ns rebel-readline.tools
  (:require
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.jline-api :as api])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedStyle]))

;; ----------------------------------------------
;; Extra Abilities
;; ----------------------------------------------

;; Color
;; ----------------------------------------------

(defn color [sk]
  (->
   (get @api/*line-reader* :color-theme)
   colors/color-themes
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
