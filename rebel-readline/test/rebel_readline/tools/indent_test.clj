(ns rebel-readline.tools.indent-test
  (:require
   [rebel-readline.tools.indent :refer :all]
   [clojure.test :refer [deftest is are testing]]))

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
