(ns rebel-readline.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string  :as string]
   [clojure.pprint  :as pp]))

(def ^:dynamic *debug-log* false)

(defn log [& args]
  (when *debug-log*
    (spit "rebel-readline-debug-log"
          (string/join ""
                       (map #(if (string? %)
                               (str % "\n")
                               (with-out-str (pp/pprint %)))
                            args))
          :append true))
  (last args))

(defn terminal-background-color? []
  (or (when-let [fgbg (System/getenv "COLORFGBG")]
        (when-let [[fg bg] (try (mapv #(Integer/parseInt (string/trim %))
                                      (string/split fgbg #";"))
                                (catch Throwable t nil))]
          (when (and fg bg)
            (if (< -1 fg bg 16)
              :light
              :dark))))
      (when-let [term-program (System/getenv "TERM_PROGRAM")]
        (let [tp (string/lower-case term-program)]
          (cond
            (.startsWith tp "iterm") :dark
            (.startsWith tp "apple") :light
            :else nil)))
      :dark))
