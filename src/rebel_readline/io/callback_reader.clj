(ns rebel-readline.io.callback-reader
  (:import
   [java.nio CharBuffer]))

(defn fill-buffer [closed-atom buf-atom read-callback]
  (when (and (not @closed-atom) (not (.hasRemaining @buf-atom)))
    (if-let [res (read-callback)]
      (reset! buf-atom (CharBuffer/wrap res))
      (vreset! closed-atom true))))

(defn callback-reader [read-callback]
  (let [buf (atom (CharBuffer/wrap ""))
        closed (volatile! false)]
      (proxy [java.io.Reader] []
        (read
          ([]
           (fill-buffer closed buf read-callback)
           (if @closed
             -1
             (let [c (.get @buf)]
               (if (integer? c) c (int c)))))
          ([^chars out-array]
           (fill-buffer closed buf read-callback)
           (if @closed
             -1
             (let [buflen (.length @buf)
                   len (min (alength out-array) buflen)]
               (.get @buf out-array 0 len)
               len)))
          ([^chars out-array off maxlen]
           (fill-buffer closed buf read-callback)
           (if @closed
             -1
             (let [buflen (.length @buf)
                   len (min buflen maxlen)]
               (.get @buf out-array off len)
               len))))
        (close []
          (vreset! closed true)))))


(comment
  (with-in-str "asdf1\nasdf2\nasdf33333"
    (let [wr (callback-reader read-line)
          ca (char-array 30)]
      (.read wr ca)
      (.read wr ca)
      (.read wr ca)
      (.read wr ca)
      (into [] ca)
      ))

  )
