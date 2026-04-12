(in-ns 'rebel-readline.clojure.main)

(defn- load-init-script
  [init-script]
  (load-file init-script))
