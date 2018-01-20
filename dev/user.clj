(ns user)

(defn test-bound-output []
  (.start
   (Thread.
    (bound-fn []
      (dotimes [n 10]
        (Thread/sleep 2000)
        (println "Testing!!"))))))

(defn test-output []
  (.start
   (Thread.
    (fn []
      (dotimes [n 10]
        (Thread/sleep 2000)
        (println "Testing!!"))))))
