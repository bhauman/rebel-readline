(ns clj-readline.line-reader
  (:refer-clojure :exclude [read-line])
  (:require
   [clj-readline.commands :as commands]
   [clj-readline.io.callback-reader :as cbr]
   [clj-readline.io.line-print-writer :as line-print-writer]
   [clj-readline.jline-api :as api]
   [clj-readline.parsing.tokenizer :as tokenize]
   [clj-readline.service.core :as srv]
   [clj-readline.tools.indent :as indent]
   [clj-readline.tools.sexp :as sexp]   
   [clj-readline.tools.syntax-highlight :as syn :refer [highlight-clj-str]]
   [clj-readline.utils :refer [log]]
   [clj-readline.widgets.base :as base-widgets]
   [clojure.string :as string]
   [clojure.main])
  (:import
   [java.nio CharBuffer]
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
   [org.jline.terminal TerminalBuilder]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

;; ---------------------------------------
;; Jline parser for Clojure
;; ---------------------------------------

;; Jline uses a parser that pulls out the words from a read line
;; it mainly uses this for word completion and line acceptance
;; I.e. is this line complete or do we need to display a secondary
;; prompt

(defn parse-line [line cursor]
  (let [tokens (tokenize/tag-words line)
        words  (filter (comp #{:string-literal-without-quotes
                               :unterm-string-literal-without-quotes
                               :word}
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
     :tokens words
     :word-token word
     :words (map first words)
     :line line
     :cursor cursor}))

(defn parsed-line [{:keys [word-index word word-cursor words tokens line cursor] :as parse-data}]
  (proxy [ParsedLine clojure.lang.IMeta] []
    (word [] word)
    (wordIndex [] word-index)
    (wordCursor [] word-cursor)
    (words [] (java.util.LinkedList. words))
    (line [] line)
    (cursor [] cursor)
    (meta [] parse-data)))

;; this is an indent call that is specific to ACCEPT_LINE based actions
;; the functionality implemented here is indenting when you hit return on a line
(defn indent [line-reader line cursor]
  ;; you could work on the buffer here instead
  ;; TODO this key binding needs to be looked up from the macro if possible
  ;; changing the buffer here is the most stable but the logic is quite different
  ;; than the current indent action
  (.runMacro line-reader (str (KeyMap/ctrl \X) (KeyMap/ctrl \I))))

;; a parser for jline that respects clojurisms
(defn make-parser []
  (doto
      (proxy [DefaultParser] []
        (isDelimiterChar [^CharSequence buffer pos]
          (boolean (#{\space \tab \return \newline  \, \{ \} \( \) \[ \] }
                    (.charAt buffer pos))))
        (parse [^String line cursor ^Parser$ParseContext context]
          (cond
            (= context Parser$ParseContext/ACCEPT_LINE)
            (when-not (srv/accept-line line cursor)
              (indent api/*line-reader* line cursor)
              (throw (EOFError. -1 -1 "Unbalanced Expression" (str *ns*))))
            (= context Parser$ParseContext/COMPLETE)
            (parsed-line (parse-line line cursor))
            :else (proxy-super parse line cursor context))))
    (.setQuoteChars (char-array [\"]))))

;; ----------------------------------------
;; Jline completer for Clojure candidates
;; ----------------------------------------

;; Completion context

(defn parsed-line-word-coords [^ParsedLine parsed-line]
  (let [pos (.cursor parsed-line)
        word-cursor (.wordCursor parsed-line)
        word (.word parsed-line)
        word-start (- pos word-cursor)
        word-end (+ pos (- (count word) word-cursor))]
    [word-start word-end]))

;; TODO this has string hacks in it that wouldn't be needed
;; with better sexp parsing 
(defn replace-word-with-prefix [parsed-line]
  (let [[start end] (parsed-line-word-coords parsed-line)
        [_ _ _ typ] (:word-token (meta parsed-line))
        line (.line parsed-line)]
    [(str (subs line 0 start)
         "__prefix__" (when (= typ :unterm-string-literal-without-quotes) \")
         (subs line end (count line)))
     (+ start (count "__prefix__")
        (if (#{:string-literal-without-quotes :unterm-string-literal-without-quotes}
             typ) 1 0))]))

(defn complete-context [^ParsedLine parsed-line]
  (when-let [[form-str end-of-marker] (replace-word-with-prefix parsed-line)]
    (when-let [valid-sexp (sexp/valid-sexp-from-point form-str end-of-marker)]
      (binding [*default-data-reader-fn* identity]
        (try (read-string valid-sexp)
             (catch Throwable e
               (log :complete-context e)
               nil))))))

(defn candidate [{:keys [candidate type ns]}]
  (proxy [Candidate] [candidate ;; value 
                      candidate ;; display
                      nil ;; group
                      (cond-> nil
                        type (str (first (name type)))
                        ns   (str " " ns)) 
                      nil ;; suffix
                      nil ;; key
                      false]
    ;; TODO remove
    ;; apparently this comparator doesn't affect final sorting 
    (compareTo [^Candidate candidate]
      (let [s1 (proxy-super value)
            s2 (.value candidate)
            res (compare (count s1) (count s2))]
        (if (zero? res)
          (compare s1 s2)
          res)))))

(defn repl-command-complete [{:keys [line tokens word]}]
  (when (and (= 1 (count tokens))
             (.startsWith (string/triml line) ":r")
             (.startsWith word ":r"))
    (->> (commands/all-commands)
         (map str)
         (filter #(.startsWith % word))
         (sort-by (juxt count identity))
         (map #(hash-map :candidate % :type :repl-command))
         not-empty)))

;; TODO abstract completion service here
(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (let [word (.word line)]
        (when (and (not (string/blank? word))
                   (pos? (count word)))
          (let [options (let [ns' (srv/current-ns)
                              context (complete-context line)]
                          (cond-> {}
                            ns'     (assoc :ns ns')
                            context (assoc :context context)))]
            (->> 
             (or
              (repl-command-complete (meta line))
              (srv/completions (.word line) options))
             (map #(candidate %))
             (take 10)
             (.addAll candidates))))))))

;; ----------------------------------------
;; Jline highlighter for Clojure code
;; ----------------------------------------

(defn highlighter []
  (proxy [Highlighter] []
    (highlight [^LineReader reader ^String buffer]
      (if (:highlight (srv/config))
        (.toAttributedString (highlight-clj-str buffer))
        (AttributedString. buffer)))))

;; ----------------------------------------
;; Building the line reader
;; ----------------------------------------

(defn line-reader* []
  (doto (-> (LineReaderBuilder/builder)
            (.terminal (-> (TerminalBuilder/builder)
                           (.build)))
            (.completer (clojure-completer))
            (.highlighter (highlighter))
            (.parser  (make-parser))
            (.build))
    ;; make sure that we don't have to double escape things
    (.setOpt LineReader$Option/DISABLE_EVENT_EXPANSION)
    ;; never insert tabs
    (.unsetOpt LineReader$Option/INSERT_TAB)
    (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P #_=> ")
    base-widgets/add-default-widgets-and-bindings
    #_add-paredit))

;; ----------------------------------------
;; Main API
;; ----------------------------------------

(defn line-reader [service]
  {:service service
   :line-reader (line-reader*)})

(defn output-handler [{:keys [line-reader service]}]
  (fn [{:keys [text]}]
    (when (not (string/blank? text))
      (binding [srv/*service* service]
        (api/reader-println line-reader text)))))

(defn stream-read-line [{:keys [service line-reader] :as reader}]
  (binding [srv/*service* service
            api/*line-reader* line-reader]
    ;; TODO consider redirecting *err* as well
    (let [save-out *out*]
      (when (:redirect-output (srv/config))
        (alter-var-root #'*out*
                        (fn [_] (line-print-writer/print-writer
                                 :out
                                 (output-handler reader)))))
      (try
        (let [res' (.readLine line-reader (srv/prompt))]
          (if-not (commands/handle-command res')
            (str res' (System/getProperty "line.separator"))
            (System/getProperty "line.separator")))
        (catch UserInterruptException e
          (System/getProperty "line.separator"))
        (catch EndOfFileException e
          nil)
        (catch clojure.lang.ExceptionInfo e
          (if (:request-exit (ex-data e))
            nil
            (throw e)))
        (finally
          (when (:redirect-output (srv/config))
            (alter-var-root #'*out* (fn [_] save-out))))))))

(defn read-line [{:keys [service line-reader] :as reader} request-prompt request-exit]
  (binding [srv/*service* service
            api/*line-reader* line-reader]
    ;; TODO consider redirecting *err* as well
    (let [save-out *out*]
      (when (:redirect-output (srv/config))
        (alter-var-root #'*out*
                        (fn [_] (line-print-writer/print-writer
                                 :out
                                 (output-handler reader)))))
      (try
        (let [res' (.readLine line-reader (srv/prompt))]
          (if-not (commands/handle-command res')
            res'
            request-prompt))
        (catch UserInterruptException e
          request-prompt)
        (catch EndOfFileException e
          request-exit)
        (catch clojure.lang.ExceptionInfo e
          (if (:request-exit (ex-data e))
            request-exit
            (throw e)))
        (finally
          (when (:redirect-output (srv/config))
            (alter-var-root #'*out* (fn [_] save-out))))))))

(defn has-remaining? [pbr]
  (let [x (.read pbr)]
    (and (not (neg? x))
         (do (.unread pbr x) true))))

(defn clj-repl-read [line-reader]
  (let [reader-buffer (atom (clojure.lang.LineNumberingPushbackReader.
                             (java.io.StringReader. "")))]
    (fn [request-prompt request-exit]
      (if (has-remaining? @reader-buffer)
        (binding [*in* @reader-buffer]
          (clojure.main/repl-read request-prompt request-exit))
        (let [possible-forms (read-line line-reader request-prompt request-exit)]
          (if (#{request-prompt request-exit} possible-forms)
            possible-forms
            (if-not (string/blank? possible-forms)
              (do
                (reset! reader-buffer (clojure.lang.LineNumberingPushbackReader.
                                       (java.io.StringReader. (str possible-forms "\n"))))
                (binding [*in* @reader-buffer]
                  (clojure.main/repl-read request-prompt request-exit)))
              request-prompt)))))))

(defmacro with-readline-input-stream [service & body]
  `(let [lr# (line-reader ~service)]
    (binding [*in* (clojure.lang.LineNumberingPushbackReader.
                    (cbr/callback-reader #(stream-read-line lr#)))]
      ~@body)))
