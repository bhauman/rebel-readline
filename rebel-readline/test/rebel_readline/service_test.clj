(ns rebel-readline.service-test
  (:require
   [rebel-readline.service :as core]
   [clojure.test :refer [deftest is are testing]]))

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
