(ns rebel-readline.io.line-print-writer
  (:require
   [clojure.string :as string])
  (:import
   [java.nio CharBuffer]
   [java.io Writer PrintWriter]))

(defn read-buf [buf]
  (.flip buf)
  (let [text (str buf)]
    (.clear buf)
    text))

(defn write-buf [^Writer writer ^CharBuffer buf ^CharSequence char-seq]
  (if (< (.length char-seq) (.remaining buf))
    (.append buf char-seq)
    (dotimes [n (.length char-seq)]
      (when-not (.hasRemaining buf)
        (.flush writer))
      (.append buf (.charAt char-seq n)))))

(defn subsequence-char-array [ch-array off len]
  (str (doto (java.lang.StringBuilder.)
         (.append ch-array off len))))

;; this print_writer pushes back uncomplete strings less than 500 chars long
;; trys to respect an output that must print newlines after flushing.
;; this is for handling output when the line reader is engaged and 
;; one needs to print the output "before" the prompt
(defn print-writer [channel-type handler]
  (let [buf (CharBuffer/allocate 1024)]
    (PrintWriter. (proxy [Writer] []
                    (close [] (.flush ^Writer this))
                    (write [& [x ^Integer off ^Integer len]]
                      (when-not (.hasRemaining buf)
                        (.flush ^Writer this))
                      (locking buf
                        (cond
                          (number? x) (.append buf (char x))
                          (not off)   (.append buf x)
                          (instance? CharSequence x)
                          (write-buf this buf (.subSequence x (int off) (int (+ len off))))
                          :else
                          (write-buf this buf (str (doto (java.lang.StringBuilder.)
                                                     (.append x off len))))))
                      (when-not (.hasRemaining buf)
                        (.flush ^Writer this)))
                    (flush []
                      (let [text (locking buf
                                   (read-buf buf))]
                        (when (pos? (count text))
                          (let [lines (string/split-lines text)
                                lines (if (and
                                           (not
                                            (.endsWith text
                                                       (System/getProperty "line.separator")))
                                           (< (count (last lines)) 500))
                                        ;; pushback
                                        (do
                                          (locking buf
                                            (.append buf (last lines)))
                                          (butlast lines))
                                        lines)]
                            (handler {:type ::output
                                      :channel channel-type
                                      :text (string/join
                                             (System/getProperty "line.separator")
                                             lines)}))))))
                  true)))

(comment

  (let [res (atom [])
        out (print-writer :out (fn [x] (swap! res conj x)))]
    (binding [*out* out]
      (println (slurp "project.clj"))
      (print "asdfasdfasdfasdfasdf  ")
      )
    (.flush out)
    (binding [*out* out]
      (println "3\n 2")
      )

    @res)

  )
