(ns rebel-readline.cljs.service.local-test
  (:require
   [cljs.analyzer :as ana]
   [cljs.compiler :as comp]
   [cljs.env :as env]
   [clojure.test :refer [deftest is testing]]
   [rebel-readline.cljs.service.local :as service]
   [rebel-readline.clojure.line-reader :as line-reader]))

(defn alias-completions
  [word]
  (env/with-compiler-env (env/default-compiler-env {})
    (comp/with-core-cljs {}
      (fn []
        (binding [ana/*cljs-ns* 'cljs.user]
          (ana/analyze (ana/empty-env)
                       '(ns cljs.user
                          (:require [demo.local :as dl]))))
        (line-reader/-complete (service/create) word {:ns "cljs.user"})))))

(deftest local-alias-completion-test
  (testing "completes vars from a locally required CLJS namespace"
    (is (= #{"dl/local-alpha" "dl/local-beta" "dl/local-value"}
           (set (map (comp str :candidate)
                     (alias-completions "dl/loc")))))))
