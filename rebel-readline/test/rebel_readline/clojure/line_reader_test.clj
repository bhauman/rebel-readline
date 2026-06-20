(ns rebel-readline.clojure.line-reader-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is are testing]]
   [rebel-readline.clojure.line-reader :as core :refer [indent-proxy-str]])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute PosixFilePermissions]))

#_(remove-ns 'rebel-readline.clojure.line-reader-test)

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
  #_(is (not (core/default-accept-line "()(" 2))))

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

(deftest candidate-test
  (testing "coerces symbolic completion candidates for JLine"
    (let [candidate (core/candidate {:candidate 'dl/local-alpha
                                     :type :function
                                     :ns 'demo.local})]
      (is (= "dl/local-alpha" (.value candidate)))
      (is (= "dl/local-alpha" (.displ candidate))))))

(deftest ensure-secure-history-file-test
  (let [temp-dir (.toFile (Files/createTempDirectory "rebel-history-test"
                                                     (make-array java.nio.file.attribute.FileAttribute 0)))
        history-file (io/file temp-dir ".rebel_readline_history")
        get-permissions #(Files/getPosixFilePermissions
                          (.toPath history-file)
                          (make-array java.nio.file.LinkOption 0))]
    (try
      (if (core/posix-file-attributes-supported? history-file)
        (do
          (testing "creates history file with owner-only permissions"
            (is (= (str history-file)
                   (core/ensure-secure-history-file! history-file)))
            (is (= (PosixFilePermissions/fromString "rw-------")
                   (get-permissions))))

          (testing "tightens permissions on an existing history file"
            (Files/setPosixFilePermissions
             (.toPath history-file)
             (PosixFilePermissions/fromString "rw-r--r--"))
            (core/ensure-secure-history-file! history-file)
            (is (= (PosixFilePermissions/fromString "rw-------")
                   (get-permissions)))))
        (testing "creates history file on non-POSIX filesystems"
          (is (= (str history-file)
                 (core/ensure-secure-history-file! history-file)))
          (is (.exists history-file))))
      (finally
        (Files/deleteIfExists (.toPath history-file))
        (Files/deleteIfExists (.toPath temp-dir))))))
