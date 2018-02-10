(ns rebel-readline.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string  :as string]
   [clojure.pprint  :as pp]))

(def ^:dynamic *debug-log* false)

(defn log [& args]
  (when *debug-log*
    (spit "rebel-readline-debug-log"
          (string/join "\n"
                       (map #(if (string? %)
                               %
                               (with-out-str (pp/pprint %)))
                            args))
          :append true))
  (last args))

(defn terminal-background-color? []
  (when-let [term-program (System/getenv "TERM_PROGRAM")]
    (let [tp (string/lower-case term-program)]
      (cond
        (.startsWith tp "iterm") :dark
        (.startsWith tp "apple") :light
        :else :dark))))
