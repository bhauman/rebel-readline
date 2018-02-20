(ns rebel-readline.clojure.line-reader-test
  (:require
   [rebel-readline.clojure.line-reader :as core :refer [indent-proxy-str]]
   [clojure.test :refer [deftest is are testing]]))

#_ (remove-ns 'rebel-readline.clojure.line-reader-test)

(deftest default-accept-line-test
  (is (core/default-accept-line "()" 0))
  (is (not (core/default-accept-line "()" 1)))
  (is (core/default-accept-line "()" 2))
  (is (not (core/default-accept-line "()(" 3)))

  (is (core/default-accept-line " \"2345" 1))
  (is (not (core/default-accept-line " \"2345" 2)))
  (is (not (core/default-accept-line " \"2345" 5)))
  (is (not (core/default-accept-line " \"2345" 6)))

  (is (not (core/default-accept-line " \"2345\"" 2)))
  (is (not (core/default-accept-line " \"2345\"" 5)))
  (is (not (core/default-accept-line " \"2345\"" 6)))
  (is (core/default-accept-line " \"2345\"78" 7))
  (is (core/default-accept-line " \"2345\"78" 8))

  ;; don't accept a line if there is an imcomplete form at the end
  ;; TODO not sure about this behavior
  #_(is (not (core/default-accept-line "()(" 2)))

  )


(deftest indent-proxy-str-test
  (let [tstr "(let [x 1] mark11 (let [y 2] mark29 (list 1 2 3 mark48"]
    (is (=   "(let [x 1] \n1)"
             (indent-proxy-str tstr 11)))
    (is (=   "                  (let [y 2] \n1)"
             (indent-proxy-str tstr 29)))
    (is (=   "                                    (list 1 2 3 \n1)"
             (indent-proxy-str tstr 48))))

  (testing "correct bondaries"
    (is (= "(list \n1)"
           (indent-proxy-str "(list ()" 6)))
    (is (= "      (\n1)"
           (indent-proxy-str "(list ()" 7)))
    (is (= "(list ()\n1)"
           (indent-proxy-str "(list ()  " 8))))

  ;; don't indent strings
  (is (= "(list \n1)"
       (indent-proxy-str "(list \"hello\"" 6)))
  (is (not (indent-proxy-str "(list \"hello\"" 7)))
  (is (not (indent-proxy-str "(list \"hello\"" 12)))
  (is (indent-proxy-str "(list \"hello\"" 13)))
