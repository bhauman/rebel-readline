(ns user)

(defn test-bound-output []
  (.start
   (Thread.
    (bound-fn []
      (dotimes [n 10]
        (Thread/sleep 2000)
        (println "Testing Bound!!"))))))

(defn test-output []
  (.start
   (Thread.
    (fn []
      (dotimes [n 10]
        (Thread/sleep 2000)
        (println "Testing!!"))))))

(defn complex [depth]
  (if (zero? depth)
    depth
    (list (complex (dec depth)) (complex (dec depth)))))

#_(complex 10)
