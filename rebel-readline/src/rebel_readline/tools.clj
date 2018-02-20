(ns rebel-readline.tools
  (:require
   [rebel-readline.tools.colors :as colors]
   [rebel-readline.jline-api :as api])
  (:import
   [org.jline.utils AttributedStyle]))

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
  "returns a repl prompt string"
  service-dispatch)

(defmethod -prompt :default [_] "")

;; TODO this is a good start
(defn prompt []
  (if-let [f (resolve-fn? (:prompt @api/*line-reader*))]
    (f)
    (-prompt @api/*line-reader*)))
