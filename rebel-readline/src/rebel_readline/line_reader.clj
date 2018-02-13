(ns rebel-readline.line-reader
  (:refer-clojure :exclude [read-line])
  (:require
   [rebel-readline.commands :as commands]
   [rebel-readline.io.callback-reader :as cbr]
   [rebel-readline.io.line-print-writer :as line-print-writer]
   [rebel-readline.jline-api :as api]
   [rebel-readline.parsing.tokenizer :as tokenize]
   [rebel-readline.service.core :as srv]
   [rebel-readline.tools.indent :as indent]
   [rebel-readline.tools.sexp :as sexp]
   [rebel-readline.tools.syntax-highlight :as syn :refer [highlight-clj-str]]
   [rebel-readline.utils :refer [log]]
   [rebel-readline.widgets.base :as base-widgets]
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
   [org.jline.terminal.impl DumbTerminal]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

;; ---------------------------------------
;; Jline parser for Clojure
;; ---------------------------------------

;; Jline uses a parser that pulls out the words from a read line
;; it mainly uses this for word completion and line acceptance
;; I.e. is this line complete or do we need to display a secondary
;; prompt?

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

  ;; a two key macro here adds a slight delay to the indent action I think
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
  (Candidate.
   candidate ;; value
   candidate ;; display
   nil ;; group
   (cond-> nil
     type (str (first (name type)))
     ns   (str (when type " ") ns))
   nil ;; suffix
   nil ;; key
   false))

(defn command-token? [{:keys [line tokens word]} starts-with]
  (and (= 1 (count tokens))
       (.startsWith (string/triml line) starts-with)
       (.startsWith word starts-with)))

(defn find-completions [candidates prefix]
  (->> candidates
       (map str)
       (filter #(.startsWith % prefix))
       (sort-by (juxt count identity))
       (map #(hash-map :candidate % :type :repl-command))
       not-empty))

(defn repl-command-complete [{:keys [word] :as parsed-line}]
  (when (command-token? parsed-line ":r")
    (find-completions (commands/all-commands) word)))

(defn cljs-quit-complete [{:keys [word] :as parsed-line}]
  (when (command-token? parsed-line ":c")
    (find-completions [:cljs/quit] word)))

;; TODO abstract completion service here
(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (let [word (.word line)]
        (when (and
               (:completion (srv/config))
               (not (string/blank? word))
               (pos? (count word)))
          (let [options (let [ns' (srv/current-ns)
                              context (complete-context line)]
                          (cond-> {}
                            ns'     (assoc :ns ns')
                            context (assoc :context context)))]
            (->>
             (or
              (repl-command-complete (meta line))
              (cljs-quit-complete (meta line))
              (srv/completions (.word line) options))
             (map #(candidate %))
             (take 10)
             (.addAll candidates))))))))

;; ----------------------------------------
;; Jline highlighter for Clojure code
;; ----------------------------------------

(defn clojure-highlighter [service]
  (proxy [Highlighter] []
    (highlight [^LineReader reader ^String buffer]
      ;; this gets called on a different thread
      ;; by the window resize interrupt handler
      ;; so add these bindings here
      (binding [srv/*service* service
                api/*line-reader* reader]
        (if (:highlight (srv/config))
          (.toAttributedString (highlight-clj-str buffer))
          (AttributedString. buffer))))))

;; ----------------------------------------
;; Create a terminal
;; ----------------------------------------

(defn assert-system-terminal [terminal]
  (when (instance? DumbTerminal terminal)
    (throw (ex-info "Unable to create a system Terminal, you must
not launch the Rebel readline from an intermediate process i.e if you
are using `lein` you need to use `lein trampoline`." {:type ::bad-terminal}))))

(defn create-terminal [& [assert-system-terminal']]
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.build))]
    (when (not (false? assert-system-terminal'))
      (assert-system-terminal terminal))
    terminal))

;; ----------------------------------------
;; Building the line reader
;; ----------------------------------------

(defn line-reader* [service & [{:keys [terminal
                                       completer
                                       highlighter
                                       parser
                                       assert-system-terminal]
                                :as options}]]
  (doto (-> (LineReaderBuilder/builder)
            (.terminal (or terminal
                           (create-terminal assert-system-terminal)))
            (.completer (or completer (clojure-completer)))
            (.highlighter (or highlighter (clojure-highlighter service)))
            (.parser  (or parser (make-parser)))
            (.build))
    ;; make sure that we don't have to double escape things
    (.setOpt LineReader$Option/DISABLE_EVENT_EXPANSION)
    ;; never insert tabs
    (.unsetOpt LineReader$Option/INSERT_TAB)
    (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P #_=> ")
    (base-widgets/add-default-widgets-and-bindings service)
    #_add-paredit))
