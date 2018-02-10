(ns rebel-readline.utils
  (:require
   [clojure.java.io :as io]
   [clojure.string  :as string]
   [clojure.pprint  :as pp]))

(def ^:dynamic *debug-log* false)

(defn log [& args]
  (when *debug-log*
    (spit "debug-log"
          (string/join "\n"
                       (map #(if (string? %)
                               %
                               (with-out-str (pp/pprint %)))
                            args))
          :append true))
  (last args))
