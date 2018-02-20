(ns rebel-readline.clojure.sexp-test
  (:require
   [rebel-readline.clojure.tokenizer :as tokenize]
   [rebel-readline.clojure.sexp :refer :all]
   [clojure.test :refer [deftest is are testing]]))

(defn find-open [sexp pos]
  (find-open-sexp-start (tokenize/tag-sexp-traversal sexp) pos))

(def find-open-pos (comp second find-open))

(deftest find-open-sexp-start-test
  (is (= 4 (find-open-pos "0123(5" 20)))
  (is (= 4 (find-open-pos "0123(5" 6)))
  (is (= 4 (find-open-pos "0123(5" 5)))
  ;; position is a cursor position
  (is (nil? (find-open-pos "0123(5" 4)))
  (is (= 4 (find-open-pos "0123(5)" 6)))
  (is (nil? (find-open-pos "0123(5)" 7)))

  (is (= 4 (find-open-pos "0123[5" 20)))
  (is (= 4 (find-open-pos "0123{5" 20)))

  ;; more complex example
  (is (= 6 (find-open-pos "0123(5[7{9}" 20)))
  (is (= 6 (find-open-pos "0123(5[7{9}" 11)))
  (is (= 8 (find-open-pos "0123(5[7{9}" 10)))
  (is (= 8 (find-open-pos "0123(5[7{9}" 9)))
  (is (= 6 (find-open-pos "0123(5[7{9}" 8)))
  (is (= 6 (find-open-pos "0123(5[7{9}" 7)))
  (is (= 4 (find-open-pos "0123(5[7{9}" 6)))
  (is (= 4 (find-open-pos "0123(5[7{9}" 5)))
  (is (nil? (find-open-pos "0123(5[7{9}" 4)))
  (is (nil? (find-open-pos "0123(5[7{9}" 3)))
  (is (nil? (find-open-pos "0123(5[7{9}" 1)))
  (is (nil? (find-open-pos "0123(5[7{9}" 0)))
  (is (nil? (find-open-pos "0123(5[7{9}" -1)))

  (testing "strings"
    (is (not (find-open-pos "0123\"56\"8\"ab" 4)))
    (is (= 4 (find-open-pos "0123\"56\"8\"ab" 5)))
    (is (= 4 (find-open-pos "0123\"56\"8\"ab" 6)))
    (is (= 4 (find-open-pos "0123\"56\"8\"ab" 7)))
    (is (not (find-open-pos "0123\"56\"8\"ab" 8)))
    (is (not (find-open-pos "0123\"56\"8\"ab" 9)))
    (is (= 9 (find-open-pos "0123\"56\"8\"ab" 10)))
    (is (= 9 (find-open-pos "0123\"56\"8\"ab" 11)))
    (is (= 9 (find-open-pos "0123\"56\"8\"ab" 20)))
    (is (= 9 (find-open-pos "0123\"56\"8\"ab" 20))))

  )

(defn find-end [sexp pos]
  (find-open-sexp-end (tokenize/tag-sexp-traversal sexp) pos))

(def find-end-pos (comp second find-end))

(deftest find-open-sexp-end-test
  (is (= 4 (find-end-pos "0123)5" 0)))
  (is (= 4 (find-end-pos "0123)5" 2)))
  (is (= 4 (find-end-pos "0123)5" 3)))
  (is (= 4 (find-end-pos "0123)5" 4)))
  ;; position is a cursor position
  (is (nil? (find-end-pos "0123)5" 5)))

  (is (nil? (find-end-pos "0123(5)" 3)))
  (is (nil? (find-end-pos "0123(5)" 4)))
  (is (= 6 (find-end-pos "0123(5)" 5)))
  (is (= 6 (find-end-pos "0123(5)" 6)))
  (is (nil? (find-end-pos "0123(5)" 7)))

  (is (= 5 (find-end-pos "01234]6" 0)))
  (is (= 5 (find-end-pos "01234}6" 4)))

  ;; more complex example
  (is (= 7 (find-end-pos "012{4}6]8)a" -1)))
  (is (= 7 (find-end-pos "012{4}6]8)a" 0)))
  (is (= 7 (find-end-pos "012{4}6]8)a" 1)))
  (is (= 7 (find-end-pos "012{4}6]8)a" 2)))
  (is (= 7 (find-end-pos "012{4}6]8)a" 3)))

  (is (= 5 (find-end-pos "012{4}6]8)a" 4)))
  (is (= 5 (find-end-pos "012{4}6]8)a" 5)))

  (is (= 7 (find-end-pos "012{4}6]8)a" 6)))
  (is (= 7 (find-end-pos "012{4}6]8)a" 7)))

  (is (= 9 (find-end-pos "012{4}6]8)a" 8)))

  (is (= 9 (find-end-pos "012{4}6]8)a" 9)))
  (is (nil? (find-end-pos "012{4}6]8)a" 10)))

  (is (not (find-end-pos "012\"45\"78" 3)))
  (is (= 6 (find-end-pos "012\"45\"78" 4)))
  (is (= 6 (find-end-pos "012\"45\"78" 5)))
  (is (= 6 (find-end-pos "012\"45\"78" 6)))
  (is (not (find-end-pos "012\"45\"78" 7)))

  )

(defn in-quote* [sexp pos]
  (in-quote? (tokenize/tag-sexp-traversal sexp) pos))

(deftest in-quote-test
  (is (not (in-quote* "0123\"56\"8\"ab" 3)))
  (is (not (in-quote* "0123\"56\"8\"ab" 4)))
  (is (in-quote* "0123\"56\"8\"ab" 5))
  (is (in-quote* "0123\"56\"8\"ab" 6))
  (is (in-quote* "0123\"56\"8\"ab" 7))
  (is (not (in-quote* "0123\"56\"8\"ab" 8)))
  (is (not (in-quote* "0123\"56\"8\"ab" 9)))
  (is (in-quote* "0123\"56\"8\"ab" 10))
  (is (in-quote* "0123\"56\"8\"ab" 11))
  (is (in-quote* "0123\"56\"8\"ab" 12))
  (is (not (in-quote* "0123\"56\"8\"ab" 13)))

  (is (not (in-quote* "012 \\a" 3)))
  (is (not (in-quote* "012 \\a" 4)))
  (is (in-quote* "012 \\a" 5))
  (is (in-quote* "012 \\a" 6))
  (is (not (in-quote* "012 \\a    " 7)))

  )

(defn in-line-comment* [sexp pos]
  (in-line-comment? (tokenize/tag-sexp-traversal sexp) pos))

(deftest in-line-comment-test
  (is (in-line-comment* "012;456" 4))
  (is (in-line-comment* "012;456" 5))
  (is (in-line-comment* "012;456" 6))
  (is (in-line-comment* "012;456" 7))
  (is (not (in-line-comment* "012;456" 8)))
  (is (in-line-comment* "012;456\n" 7))
  (is (not (in-line-comment* "012;456\n" 8)))
  )

(deftest valid-sexp-from-point-test
  (is (= "(do (let [x y] (.asdf sdfsd)))"
         (valid-sexp-from-point "      (do (let [x y] (.asdf sdfsd)    " 22)))
  (is (= "(do (let [x y]))"
         (valid-sexp-from-point "      (do (let [x y] (.asdf sdfsd)    " 20)))
  (is (= "(do (let [x y]  ))"
         (valid-sexp-from-point "      (do (let [x y]     " 22)))
  (is (= "([{(\"hello\")}])"
         (valid-sexp-from-point "  ([{(\"hello\"     " 10)))
  (is (= "{(\"hello\")}"
         (valid-sexp-from-point "  ([{(\"hello\")})     " 10)))



  )

(deftest word-at-position-test
  (is (not (word-at-position " 1234 " 0)))
  (is (= ["1234" 1 5 :word]
         (word-at-position " 1234 " 1)))
  (is (= ["1234" 1 5 :word]
         (word-at-position " 1234 " 4)))
  (is (= ["1234" 1 5 :word]
         (word-at-position " 1234 " 5)))
  (is (not (word-at-position " 1234 " 6)))

  )

(deftest sexp-ending-at-position-test
  (is (= ["(34)" 2 6 :sexp]
         (sexp-ending-at-position "01(34)" 5)))
  (is (= ["\"34\"" 2 6 :sexp]
         (sexp-ending-at-position "01\"34\"" 5)))
  (is (not (sexp-ending-at-position "01(34)" 4)))
  (is (not (sexp-ending-at-position "01\"34\"" 4)))
  (is (not (sexp-ending-at-position "01(34)" 1)))
  (is (not (sexp-ending-at-position "01\"34\"" 1)))

  (is (not (sexp-ending-at-position "01(34)" 6)))
  (is (not (sexp-ending-at-position "01\"34\"" 6)))

  )
