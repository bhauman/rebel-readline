(ns clj-readline.tools.read-forms)

(defn read-forms
  "Reads all the forms in a given string, if it encounters an EOF
  because of an open delimiter it will return the
  `open-delimiter-error-marker` in the list of results. If it
  encounters another exception it will return the exception in the
  list as well.

  This function is primarily used to determine wether a the line
  reader should accept the given buffer of text or wether it should
  display a secondary prompt."
  [line-str open-delimiter-error-marker]
  (let [reader (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. line-str))
        end (Object.)]
    (take-while
     #(not= % end)
     (repeatedly
      #(binding [*default-data-reader-fn* (fn [a b] b)]
         (try
           (read {:eof end} reader)
           (catch clojure.lang.LispReader$ReaderException e
             (if (.. e
                     getCause
                     getMessage
                     (startsWith "EOF while reading")) 
               open-delimiter-error-marker
               e))))))))

#_(read-forms "(1 2 3  " :ehhh?)

(defn default-accept-line
  "Given a string containing clojure forms `line-str` and a `cursor`
  index into the string, this function will return true as long as the
  expression \"upto the cursor\" doesn't have a dangling open
  delimiter in it. This will return true for a blank / empty line.

  This function is used to determine wether a the line reader should
  accept the given buffer of text or wether it should display a
  secondary prompt."
  [line-str cursor]
  (let [x (subs line-str 0 (min (count line-str) cursor))
        open-delimiter-marker (Object.)]
    (not (some #(= open-delimiter-marker %)
               (read-forms x open-delimiter-marker)))))
