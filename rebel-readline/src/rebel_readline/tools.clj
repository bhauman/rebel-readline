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
