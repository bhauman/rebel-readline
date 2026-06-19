(ns rebel-readline.clojure.service.local-test
  (:require
   [clojure.test :refer [deftest is]]
   [rebel-readline.clojure.line-reader :as clj-reader]
   [rebel-readline.clojure.service.local :as local]))

(defn rebel-local-completion-fixture [])

(deftest complete-coerces-string-ns-option
  (let [results (clj-reader/-complete
                 (local/create {})
                 "rebel-local-completion-fixture"
                 {:ns "rebel-readline.clojure.service.local-test"})]
    (is (some #(= "rebel-local-completion-fixture" (:candidate %)) results))))
