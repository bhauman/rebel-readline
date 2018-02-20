(ns rebel-readline.main
  (:require
   [rebel-readline.clojure.main :as main]))

(defn -main [& args]
  (apply main/-main args))
