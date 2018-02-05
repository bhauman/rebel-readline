(ns rebel-readline-paredit.core
  (:require
   [rebel-readline.tools.indent :as ind])
  (:use rebel-readline.jline-api)
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.reader LineReader]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle
    #_InfoCmp$Capability]
   [java.util.regex Pattern]))

;; ----------------------------------------------
;; this is a WIP and is out of date right now
;; ----------------------------------------------

;; ------------
;; paredit open

(defn should-self-insert-open? [code-str cursor]
  #_(log :hey code-str cursor (ind/in-non-interp-bounds? code-str cursor))
  (if-let [[start end token-type] (ind/in-non-interp-bounds? code-str cursor)]
    (not (and (= :character token-type)
              (not (= end (inc start)))))
    true))

#_(and (should-self-insert-open?  " \\ a  " 2)
     (not (should-self-insert-open?  " \\a  " 2))
     )
#_(ind/in-non-interp-bounds? " \\  " (dec 2))

(defn paredit-insert-pair [open-char buffer]
  (let [cursor (.cursor buffer)]
    (when-not (or (ind/blank-at-position? (str buffer) (dec cursor))
                  (#{\( \{ \[} (char (.prevChar buffer))))
      (.write buffer (int \space)))
    (doto buffer
      (.write (int open-char))
      (.write (int (ind/flip-delimiter open-char)))
      (.move  -1))
    (when-not (or (ind/blank-at-position? (str buffer) (inc (.cursor buffer)))
                  (#{\) \} \]} (char (.nextChar buffer))))
      (.move buffer 1)
      (.write buffer (int \space))
      (.move buffer -2))
    true))

(defn paredit-open [open-char buffer]
  (let [cursor (.cursor buffer)
        s (str buffer)]
    (if (ind/in-non-interp-bounds? s cursor)
      (not (should-self-insert-open? s cursor))
      (paredit-insert-pair open-char buffer))))

(defn paredit-open-widget [open-char]
  (create-widget
   (if (paredit-open open-char *buffer*)
     true
     (do (call-widget LineReader/SELF_INSERT)
         true))))

;; ------------
;; paredit close

(comment

  (let [b (buffer "(       )")]
    (.move b -1)
    (paredit-close-action b)
  b
  )
  
  (list 1
      2
      3)

(ind/find-open-sexp-end (ind/tag-for-sexp-traversal "()") 1)

)



(defn backwards-clean-whitespace [buffer]
  (loop []
    (when (Character/isWhitespace (.prevChar buffer))
      (.backspace buffer)
      (recur))))

(defn paredit-close-action [buffer]
  (let [s (str buffer)
        tagged-parses (ind/tag-for-sexp-traversal s)
        [_ start _ _] (ind/find-open-sexp-end tagged-parses (.cursor buffer))]
    (if-not start
      false
      (do
        (.cursor buffer start)
        (backwards-clean-whitespace buffer)
        ;; TODO blink by calling widget a
        (.move buffer 1)
        true))))

(defn paredit-close [buffer]
  (let [cursor (.cursor buffer)
        s (str buffer)]
    (if (ind/in-non-interp-bounds? s cursor)
      (and (not (should-self-insert-open? s cursor)) :self-insert)
      (paredit-close-action buffer))))

(defn paredit-close-widget [line-reader]
  (create-widget
   (condp = (paredit-close *buffer*)
     :self-insert
     (do (call-widget LineReader/SELF_INSERT)
         true)
     true
     (do
       #_(.move buf -1)
       #_(.callWidget line-reader LineReader/VI_MATCH_BRACKET)
       #_(future
           (do
             (Thread/sleep 500)
             (.callWidget line-reader LineReader/VI_MATCH_BRACKET)
             (.move buf 1)))
       true)
     false false)))

#_(defn add-paredit [line-reader]
  (-> line-reader
      (register-widget "paredit-open-paren"   (paredit-open-widget \( line-reader))
      (register-widget "paredit-open-brace"   (paredit-open-widget \{ line-reader))
      (register-widget "paredit-open-bracket" (paredit-open-widget \[ line-reader))
      (register-widget "paredit-open-quote"   (paredit-open-widget \" line-reader))
      (register-widget "paredit-close"        (paredit-close-widget line-reader))

      (bind-key "paredit-open-paren"   (str "("))
      (bind-key "paredit-open-brace"   (str "{"))
      (bind-key "paredit-open-bracket" (str "["))
      
      (bind-key "paredit-close"   (str ")"))
      (bind-key "paredit-close"   (str "}"))
      (bind-key "paredit-close"   (str "]"))

      ;; TODO backspace
      
      )
  )
