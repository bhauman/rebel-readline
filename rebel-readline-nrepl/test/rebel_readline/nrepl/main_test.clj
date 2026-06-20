(ns rebel-readline.nrepl.main-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [rebel-readline.nrepl.main :as main]
   [rebel-readline.tools :as tools]))

(deftest read-port-file-test
  (let [dir (java.nio.file.Files/createTempDirectory "rebel-nrepl-test" (make-array java.nio.file.attribute.FileAttribute 0))
        port-file (io/file (.toFile dir) ".nrepl-port")]
    (try
      (spit port-file "16670\n")
      (is (= 16670 (main/read-port-file port-file)))
      (spit port-file "not-a-port\n")
      (is (nil? (main/read-port-file port-file)))
      (finally
        (.delete port-file)
        (.delete (.toFile dir))))))

(deftest resolve-options-test
  (testing "explicit port wins"
    (is (= {:port 1234}
           (main/resolve-options {:port 1234
                                  :port-file "ignored.nrepl-port"}))))

  (testing ".nrepl-port is used when no port is configured"
    (let [dir (java.nio.file.Files/createTempDirectory "rebel-nrepl-test" (make-array java.nio.file.attribute.FileAttribute 0))
          port-file (io/file (.toFile dir) ".nrepl-port")]
      (try
        (spit port-file "16670\n")
        (with-redefs [main/default-port-file (str port-file)]
          (is (= {:port 16670}
                 (main/resolve-options {}))))
        (finally
          (.delete port-file)
          (.delete (.toFile dir))))))

  (testing "configured port file is used when no port is configured"
    (let [dir (java.nio.file.Files/createTempDirectory "rebel-nrepl-test" (make-array java.nio.file.attribute.FileAttribute 0))
          port-file (io/file (.toFile dir) "project-a.nrepl-port")]
      (try
        (spit port-file "16671\n")
        (with-redefs [main/default-port-file "missing-.nrepl-port"]
          (is (= {:port 16671}
                 (main/resolve-options {:port-file (str port-file)}))))
        (finally
          (.delete port-file)
          (.delete (.toFile dir)))))))

(deftest conform-options-test
  (testing "reports a missing explicit port file"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"port file .* was not found"
         (main/conform-options {:port-file "missing-.nrepl-port"}))))

  (testing "reports an invalid explicit port file"
    (let [dir (java.nio.file.Files/createTempDirectory "rebel-nrepl-test" (make-array java.nio.file.attribute.FileAttribute 0))
          port-file (io/file (.toFile dir) "bad.nrepl-port")]
      (try
        (spit port-file "not-a-port\n")
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"did not contain a valid port"
             (main/conform-options {:port-file (str port-file)})))
        (finally
          (.delete port-file)
          (.delete (.toFile dir))))))

  (testing "reports a friendly missing port error after .nrepl-port fallback"
    (with-redefs [main/default-port-file "missing-.nrepl-port"]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Must supply an nREPL port.*port-file"
           (main/conform-options {}))))))

(deftest start-repl-background-print-precedence-test
  (testing "user config cannot override the nREPL background print default"
    (let [config (atom nil)]
      (with-redefs [tools/user-config (fn [_ _] {:port 1234
                                                 :background-print false})
                    main/start-repl* (fn [options] (reset! config options))]
        (main/start-repl {})
        (is (true? (:background-print @config))))))

  (testing "explicit options can override the nREPL background print default"
    (let [config (atom nil)]
      (with-redefs [tools/user-config (fn [_ _] {:port 1234
                                                 :background-print true})
                    main/start-repl* (fn [options] (reset! config options))]
        (main/start-repl {:background-print false})
        (is (false? (:background-print @config)))))))
