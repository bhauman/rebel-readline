(ns clj-readline.clj-linereader
  (:require
   [clj-readline.clj-widgets.base-widgets :as base-widgets]
   [clj-readline.read-forms :as forms]
   [clj-readline.syntax-highlight :as syn :refer [highlight-clj-str]]
   [clj-readline.parsing.tokenizer :as tokenize]
   [clojure.string :as string]
   [compliment.core :as compliment]
   [clj-readline.jline-api :as api])
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.reader
    Highlighter
    Completer
    Candidate
    Parser
    Parser$ParseContext
    ParsedLine
    LineReader
    LineReader$Option
    LineReaderBuilder
    UserInterruptException
    EndOfFileException
    EOFError
    Widget]
   [org.jline.reader.impl DefaultParser BufferImpl]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]
   [org.jline.terminal TerminalBuilder]))

;; fixing the Jline parser to understand clojure code

;; ---------------------------------------
;; Jline parser for Clojure
;; ---------------------------------------

;; Jline uses a parser that pulls out the words from a read line
;; it mainly uses this for word completion and line acceptance
;; I.e. is this line complete or do we need to display a secondary
;; prompt

;; TODO we could make string literals and unterm string literals complete
;; on their contents
(defn parse-line [line cursor]
  (let [tokens (tokenize/tag-words line)
        words  (filter (comp #{:string-literal
                               :unterm-string-literal
                               :complete-word}
                             last)
                       tokens)
        word   (first (filter
                       (fn [[_ s e]]
                         (<= s cursor e))
                       words))
        word-index (.indexOf (vec words) word)
        word-cursor (if-not word
                      0
                      (- cursor (second word)))]
    ;; TODO this can return a parsedline directly
    ;; but for now this is easier to debug
    {:word-index (or word-index -1)
     :word  (or (first word) "")
     :word-cursor word-cursor
     :tokens tokens
     :words (map first words)
     :line line
     :cursor cursor}))


(defn parsed-line [{:keys [word-index word word-cursor words line cursor]}]
  (proxy [ParsedLine] []
    (word [] word)
    (wordIndex [] word-index)
    (wordCursor [] word-cursor)
    (words [] (java.util.LinkedList. words))
    (line [] line)
    (cursor [] cursor)))

(defn has-valid-forms-at? [s pos]
  (when-let [forms (not-empty (forms/take-valid-forms-at s pos))]
    (and (not-empty (filter :source forms))
         (not (:exception (last forms))))))

;; this is an indent call that is specific to ACCEPT_LINE based actions
;; the functionality implemented here is indenting when you hit return on a line
(defn indent [line-reader line cursor]
  ;; you could work on the buffer here instead
  ;; TODO this key binding needs to be looked up from the macro if possible
  ;; changing the buffer here is the most stable but the logic is quite different
  ;; than the current indent action
  (.runMacro line-reader (str (KeyMap/ctrl \X) (KeyMap/ctrl \I))))

;; a parser for jline that respects clojurisms
;; still rough
;; TODO line acceptance needs to be parameterized
;; TODO should behave just like the 'reply' project here
(defn make-parser [line-reader-prom]
  (doto
      (proxy [DefaultParser] []
        (isDelimiterChar [^CharSequence buffer pos]
          (boolean (#{\space \tab \return \newline  \, \{ \} \( \) \[ \] }
                    (.charAt buffer pos))))
        (parse [^String line cursor ^Parser$ParseContext context]
          (cond
            (= context Parser$ParseContext/ACCEPT_LINE)
            (when-not (has-valid-forms-at? line cursor)
              (indent @line-reader-prom line cursor)
              (throw (EOFError. -1 -1 "Unbalanced Expression" (str *ns*))))
            (= context Parser$ParseContext/COMPLETE)
            (parsed-line (parse-line line cursor))
            :else (proxy-super parse line cursor context))))
    (.setQuoteChars (char-array [\"]))))

;; ----------------------------------------
;; Jline completer for Clojure candidates
;; ----------------------------------------

(defn candidate [{:keys [candidate type ns]}]
  (proxy [Candidate] [candidate ;; value 
                      candidate ;; display
                      nil ;; group
                      (cond-> nil
                        type (str (first (name type)))
                        ns   (str " " ns)) 
                      nil ;; suffix
                      nil ;; key
                      true]
    ;; apparently this comparator doesn't affect final sorting 
    (compareTo [^Candidate candidate]
      (let [s1 (proxy-super value)
            s2 (.value candidate)
            res (compare (count s1) (count s2))]
        (if (zero? res)
          (compare s1 s2)
          res)))))

;; TODO abstract completion service here
(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (let [word (.word line)]
        (when (and (not (string/blank? word))
                   (pos? (count word)))
          (.addAll candidates (take 10 (map #(candidate %)
                                            (compliment/completions (.word line))))))))))

;; ----------------------------------------
;; Jline highlighter for Clojure code
;; ----------------------------------------

(defn highlighter []
  (proxy [Highlighter] []
    (highlight [^LineReader reader ^String buffer]
      (if (:highlight @api/*state*)
        (.toAttributedString (highlight-clj-str buffer))
        (AttributedString. buffer)))))

(defn line-reader []
  (let [line-reader-prom (promise)]
    (doto (-> (LineReaderBuilder/builder)
              (.terminal (-> (TerminalBuilder/builder)
                             (.build)))
              (.completer (clojure-completer))
              (.highlighter (highlighter))
              ;; TODO get rid of promise just set the parser afterward
              (.parser  (make-parser line-reader-prom))
              (.build))
      ((fn [x] (deliver line-reader-prom x)))
      ;; make sure that we don't have to double escape things 
      (.setOpt LineReader$Option/DISABLE_EVENT_EXPANSION)
      ;; never insert tabs
      (.unsetOpt LineReader$Option/INSERT_TAB)
      #_(.unsetOpt LineReader$Option/CASE_INSENSITIVE)
      (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P #_=> ")
      base-widgets/add-default-widgets-and-bindings
      #_add-paredit)))
