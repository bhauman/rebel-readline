(ns clj-readline.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.reader :as r]
   [clojure.main]
   [clojure.repl]
   [compliment.core :as compliment]
   [cljfmt.core :refer [reformat-string]]
   [clojure.tools.reader.reader-types :as rtyp]

   [clj-readline.indenting :as ind]
   [clj-readline.syntax-highlight :refer [highlight-clj-str]]
   ;; for dev
   [clojure.tools.nrepl.server]
   [cider.nrepl]
   )
  (:import
   [org.jline.terminal TerminalBuilder]
   [org.jline.terminal Terminal$Signal] 
   [org.jline.terminal Terminal$SignalHandler]  
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
   [org.jline.keymap KeyMap]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle #_InfoCmp$Capability]
   [org.jline.reader.impl DefaultParser]
   [org.jline.reader.impl.completer StringsCompleter])
  (:gen-class))


;; this is an indent call that is specific to ACCEPT_LINE based actions
(defn indent [line-reader line cursor]
  ;; you could work on the buffer here instead
  ;; TODO this key binding needs to be looked up from the macro if possible
  ;; changing the buffer here is the most stable but the logic is quite different
  ;; than the current indent action
  (.runMacro line-reader (str (KeyMap/ctrl \X) (KeyMap/ctrl \I))))

;; validating that it is likely a readable form

(defn read-identity [tag form]
  [::reader-tag tag form])

(defn read-form [{:keys [eof rdr]}]
  (let [res
        (binding [r/*default-data-reader-fn* read-identity]
          (try
            (r/read {:eof eof
                     :read-cond :preserve} rdr)
            (catch Exception e
              {:exception e})))]
    (cond
      (= res eof) eof
      (and (map? res) (:exception res)) res
      (and (map? (meta res))
           (:source (meta res))) (assoc (meta res)
                                        :read-value res)
      :else {:read-value res
             :source (pr-str res)})))

(defn read-forms [s]
  (let [eof (Object.)]
    (->> (repeat {:rdr (rtyp/source-logging-push-back-reader s)
                  :eof eof})
         (map read-form)
         (take-while #(not= eof %)))))

(defn take-valid-forms-at [s pos]
  (read-forms (subs s 0 (min (count s) pos))))

(defn has-valid-forms-at? [s pos]
  (when-let [forms (not-empty (take-valid-forms-at s pos))]
    (and (not-empty (filter :source forms))
         (not (:exception (last forms))))))

;; a parser for jline that respects some clojurisms
(defn make-parser [line-reader-prom]
  (doto
      (proxy [DefaultParser] []
        (isDelimiterChar [^CharSequence buffer pos]
          (boolean (#{\space \tab \return \newline  \, \{ \} \( \) \[ \] }
                    (.charAt buffer pos))))
        (parse [^String line cursor ^Parser$ParseContext context]
          (if (= context Parser$ParseContext/ACCEPT_LINE)
            (when-not (has-valid-forms-at? line cursor)
              (indent @line-reader-prom line cursor)
              (throw (EOFError. -1 -1 "Unbalanced Expression" (str *ns*))))
            (proxy-super parse line cursor context))))
    (.setQuoteChars (char-array [\"]))))

;; completions

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

;; TODO - we need to get context infp in here to fully use compliment to its fullest
;; TODO - we also need to look at how strings are passed along for completion
;;        for files etc

(defn clojure-completer []
  (proxy [Completer] []
    (complete [^LineReader reader ^ParsedLine line ^java.util.List candidates]
      (.addAll candidates (take 10 (map #(candidate %)
                                       (compliment/completions (.word line))))))))

;; syntax highlighting
;; TODO reader error highlighting and feedback

(defn highlighter []
  (proxy [Highlighter] []
    (highlight [^LineReader reader ^String buffer]
      (.toAttributedString (#'highlight-clj-str buffer)))))

;; the main line reader

;; add functionality 

(defn indent-line-widget [line-reader]
  (reify Widget
    (apply [_]
      (let [buf (.getBuffer line-reader)
            cursor (.cursor buf)
            s (str buf)
            begin-of-line-pos (ind/search-for-line-start s (dec cursor))
            leading-white-space (ind/count-leading-white-space (subs s begin-of-line-pos))
            indent-amount (#'ind/indent-amount s begin-of-line-pos)
            cursor-in-leading-white-space? (< cursor
                                              (+ leading-white-space begin-of-line-pos))]
        ;; first we will remove-the whitespace
        (.cursor buf begin-of-line-pos)
        (.delete buf leading-white-space)
        (.write buf (apply str (repeat indent-amount \space)))

        ;; rectify cursor
        (when-not cursor-in-leading-white-space?
          (.cursor buf (+ indent-amount (- cursor leading-white-space)))))
      ;; return true to re-render
      true)))

(defn add-functionality [line-reader]
  (-> line-reader
      (.getWidgets)
      (.put "indent-line" (indent-line-widget line-reader)))
  (.bind (get (.getKeyMaps line-reader) "emacs")
         (org.jline.reader.Reference. "indent-line") (str
                                                      (KeyMap/ctrl \X)
                                                      (KeyMap/ctrl \I))))


(defn line-reader []
  (let [line-reader-prom (promise)]
    (doto (-> (LineReaderBuilder/builder)
              (.terminal (-> (TerminalBuilder/builder)
                             (.build)))
              (.completer (clojure-completer))
              (.highlighter (highlighter))
              (.parser  (make-parser line-reader-prom))
              (.build))
      ((fn [x] (deliver line-reader-prom x)))
      ;; make sure that we don't have to double escape things 
      (.setOpt LineReader$Option/DISABLE_EVENT_EXPANSION)
      (.setVariable LineReader/SECONDARY_PROMPT_PATTERN "%P #_=> ")
      add-functionality)
    
    ))

;; color isn't working for 256 color
(defn prompt []
  (with-out-str (clojure.main/repl-prompt))
  #_(let [sb (AttributedStringBuilder.)]
    (.styled sb (.foreground AttributedStyle/DEFAULT 33)
             (with-out-str (clojure.main/repl-prompt)))
    (.toAnsi sb)))

(defn repl-read [reader]
  (fn [request-prompt request-exit]
    (let [possible-forms (.readLine reader (prompt))
          possible-forms (read-forms possible-forms)]
      (cond (empty? possible-forms)
            request-prompt
            (first possible-forms)
            (r/read {:read-cond :allow} (rtyp/source-logging-push-back-reader
                                         (-> possible-forms first :source)))))))

(defn repl [reader]
  (clojure.main/repl
   :prompt (fn [])
   :read (repl-read reader)
   :caught (fn [e]
             (cond (= (type e) EndOfFileException)
                   (System/exit 0)
                   (= (type e) UserInterruptException) nil
                   :else (clojure.main/repl-caught e)))))

;; making a field 
#_(let [t (.getTerminal (line-reader))
        pty_field (-> t .getClass .getSuperclass (.getDeclaredField "pty"))]
    (.setAccessible pty_field true)
    (let [pty (.get pty_field t)] ;; there is also a .set Method
      pty
      (.doGetConfig pty)
      )
   
  )

(defn -main []
  ;; for development
  (clojure.tools.nrepl.server/start-server :port 7888 :handler cider.nrepl/cider-nrepl-handler)

  ;; read all garbage before starting
  
  ;; also include prompt
  (let [reader (line-reader)]
    (repl reader)
    #_(println ":::" (read-eval-loop reader))
    )
  

  
  )
