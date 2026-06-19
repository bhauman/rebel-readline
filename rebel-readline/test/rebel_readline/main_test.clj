(ns rebel-readline.main-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [rebel-readline.clojure.main :as clojure-main]
   [rebel-readline.main :as main]))

(deftest validate-args-test
  (testing "parses command line options"
    (is (= {:completion false}
           (main/validate-args ["--no-completion"])))))

(deftest main-test
  (testing "ignores nREPL -f callback arguments"
    (let [config (atom nil)]
      (with-redefs [clojure-main/main #(reset! config %)]
        (main/-main "127.0.0.1" 7888 {:transport :bencode}))
      (is (= {:rebel-readline/config {}}
             @config)))))
