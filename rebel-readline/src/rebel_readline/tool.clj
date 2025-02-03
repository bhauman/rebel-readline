(ns rebel-readline.tool
  (:require
   [clojure.spec.alpha :as s]
   [clojure.java.io :as io]
   [rebel-readline.tools :as tools]
   [rebel-readline.clojure.main :as main]))

(defn repl [options]
  (try
    (main/repl* {:rebel-readline/config options})
    (catch clojure.lang.ExceptionInfo e
      (let [{:keys [spec config] :as err} (ex-data e)]
        (when (-> err :type (= :rebel-readline/config-spec-error))
          (tools/explain-config spec config))))))
