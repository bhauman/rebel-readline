(ns rebel-readline.jline-api
  (:require
   [clojure.string :as string]
   [rebel-readline.jline-api.attributed-string :as astring])
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
   [org.jline.reader.impl LineReaderImpl]
   [org.jline.terminal Terminal TerminalBuilder Attributes Attributes$LocalFlag Attributes$InputFlag]
   [java.io Writer]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

(def ^:dynamic *terminal* nil)
(def ^:dynamic *state* nil)
(def ^:dynamic *buffer* nil)

(defn line-reader [] (:line-reader @*state*))

;; ----------------------------------------
;; Create a terminal
;; ----------------------------------------

(defn assert-system-terminal [terminal]
  (when (= "dumb" (.getType ^Terminal terminal))
    (throw (ex-info
"Unable to detect a system Terminal, you must not launch the Rebel readline
from an intermediate process.
If you are using `lein` you may need to use `lein trampoline`."
            {:type ::bad-terminal}))))

(defn create-terminal [& [assert-system-terminal']]
  (let [terminal (-> (TerminalBuilder/builder)
                     (.system true)
                     (.build))]
    (when (not (false? assert-system-terminal'))
      (assert-system-terminal terminal))
    terminal))

(defn toggle-input [^Terminal term on?]
  (let [attr ^Attributes (.getAttributes term)]
    (.setAttributes
     term
     (doto attr
       (.setLocalFlag Attributes$LocalFlag/ICANON on?)
       (.setLocalFlag Attributes$LocalFlag/ECHO on?)
       (.setInputFlag Attributes$InputFlag/IGNCR (not on?))))
    (.flush (.writer term))))

(declare display-message)

(defn widget-exec [thunk]
  (binding [*buffer* (.getBuffer (line-reader))]
    (try
      (thunk)
      (catch clojure.lang.ExceptionInfo e
        (if-let [message (.getMessage e)]
          (do
            (some-> @*state* :repl/error (reset! e))
            (display-message
             (AttributedString.
              (str "Internal REPL Error: this shouldn't happen. :repl/*e for stacktrace\n"
                   (class e) "\n" message)
              (.foreground AttributedStyle/DEFAULT AttributedStyle/RED)))
              true)
          (throw e))))))

(defmacro create-widget [& body]
  `(reify Widget
     (apply [_#]
       (widget-exec (fn [] ~@body)))))

(def get-accessible-field
  (memoize (fn [obj field-name]
             (loop [clazz (.getClass obj)]
               (when clazz
                 (if-let [field (try (.getDeclaredField clazz field-name)
                                     (catch Exception _ nil))]
                   (doto field (.setAccessible true))
                   (recur (.getSuperclass clazz))))))))

(defn supplier [f]
  (proxy [java.util.function.Supplier] []
    (get [] (f))))

;; --------------------------------------
;; Key maps
;; --------------------------------------

(defn key-map->clj [key-map]
  (mapv (juxt key val)
        (.getBoundKeys key-map)))

(defn key-map->display-data [key-map]
  (->> (key-map->clj key-map)
       (filter #(instance? org.jline.reader.Reference (second %)))
       (map (fn [[k v]]
              [(KeyMap/display k) (.name v)]))
       (filter
        (fn [[k v]]
          (not
           (#{ ;; these don't exist for some reason
              "character-search"
              "character-search-backward"
              "infer-next-history"
              ;; these are too numerous
              ;; TODO would be nice to display
              ;; rolled up abbreviations
              "self-insert"
              "digit-argument"
              "do-lowercase-version"} v))))))

(defn key-maps []
  (-> (line-reader) (.getKeyMaps)))

(defn key-map [key-map-name]
  (get (key-maps) (name key-map-name)))

(defn set-key-map! [key-map-name key-map]
  (-> (key-maps)
      (.put (name key-map-name) key-map)))

(defn set-main-key-map! [key-map-name]
  (boolean
   (when-let [km (key-map key-map-name)]
     (.put (key-maps) "main" km))))

(defn key-map-name [key-map]
  (when-let [res (first (filter #(and (= (val %) key-map)
                            (not= (key %) "main"))
                                (key-maps)))]
    (key res)))

(defn main-key-map-name []
  (key-map-name (key-map "main")))

(defn orig-key-map-clone [key-map-name]
  (get (.defaultKeyMaps (line-reader)) key-map-name))

(defn bind-key [key-map widget-id key-str]
  (when key-str
    (.bind key-map (org.jline.reader.Reference. widget-id) key-str)))

(defn key-binding [key-map-name key-str widget-name]
  (swap! *state* update-in
         [::key-bindings (keyword key-map-name)]
         #((fnil conj []) % [key-str widget-name])))

(defn apply-key-bindings [key-bindings & {:keys [look-up-keymap-fn] :or {look-up-keymap-fn orig-key-map-clone}}]
  (doall
   (for [[key-map-name bindings'] key-bindings]
     (when-let [km (look-up-keymap-fn (name key-map-name))]
       (doseq [[key-str widget-name] bindings']
         (when (and widget-name key-str)
           (bind-key km (name widget-name) key-str)))
       (set-key-map! (name key-map-name) km)))))

(defn apply-key-bindings! []
  (when-let [kbs (not-empty (::key-bindings @*state*))]
    (apply-key-bindings kbs))
  ;; user level key bindings applied after
  (when-let [kbs (not-empty (:key-bindings @*state*))]
    (apply-key-bindings kbs :look-up-keymap-fn key-map)))

;; --------------------------------------
;; contextual ANSI
;; --------------------------------------

(defn ->ansi [at-str]
  (if-let [t (and *state* (.getTerminal (line-reader)))]
    (astring/->ansi at-str t)
    (astring/->ansi at-str nil)))

;; --------------------------------------
;; Buffer operations
;; --------------------------------------

(defn cursor
  ([] (.cursor *buffer*))
  ([i] (.cursor *buffer* i)))

(defn move-cursor [offset]
  (.move *buffer* offset))

(defn delete
  ([] (.delete *buffer*))
  ([n] (.delete *buffer* n)))

(defn backspace
  ([] (.backspace *buffer*))
  ([n] (.backspace *buffer* n)))

(defn write [s]
  (.write *buffer* s))

(defn buffer-as-string []
  (str *buffer*))

(defn buffer-subs
  ([start] (.substring *buffer* start))
  ([start end] (.substring *buffer* start end)))

(defn up-to-cursor []
  (.upToCursor *buffer*))

(defn char-at [idx]
  (char (.atChar *buffer* idx)))

(defn curr-char []
  (char (.currChar *buffer*)))

(defn prev-char []
  (char (.prevChar *buffer*)))

(defn next-char []
  (char (.nextChar *buffer*)))

(defn next-char []
  (char (.nextChar *buffer*)))


;; --------------------------------------
;; Line Reader operations
;; --------------------------------------

(defn register-widget [widget-id widget]
  (-> (line-reader) (.getWidgets) (.put widget-id widget)))

(defn terminal-size []
  (let [lr (line-reader)
        size-field (get-accessible-field lr "size")]
    (when-let [sz (.get size-field lr)]
      {:rows (.getRows sz)
       :cols (.getColumns sz)})))

(defn redisplay []
  (let [lr (line-reader)]
    (locking (.writer (.getTerminal lr))
      (.redisplay lr))))

(defn block-redisplay-millis [time-ms]
  (let [writer (.writer (.getTerminal (line-reader)))]
    (.start
     (Thread.
      (fn []
        (locking writer
            (Thread/sleep time-ms)))))))

(defn display-message
  ([message]
   (display-message (line-reader) message))
  ([lr message]
   (let [post-field (get-accessible-field lr "post")]
     (.set post-field lr (supplier (fn [] (AttributedString. message)))))))

(defn rows-available-for-post-display []
  (let [rows (:rows (terminal-size))
        buffer-rows (count (string/split-lines (buffer-as-string)))]
    (max 0 (- rows buffer-rows))))

(defn reading? [line-reader]
  (.isReading line-reader))

(defn call-widget [widget-name]
  (.callWidget (line-reader) widget-name))

(defn create-line-reader [terminal app-name]
  (LineReaderImpl. terminal (or app-name "Rebel Readline") nil))

;; taken from Clojure 1.10 core.print
(defn- ^java.io.PrintWriter PrintWriter-on*
  "implements java.io.PrintWriter given flush-fn, which will be called
  when .flush() is called, with a string built up since the last call to .flush().
  if not nil, close-fn will be called with no arguments when .close is called"
  {:added "1.10"}
  [flush-fn close-fn]
  (let [sb (StringBuilder.)]
    (-> (proxy [Writer] []
          (flush []
            (when (pos? (.length sb))
              (flush-fn sb)))
          (close []
                 (.flush ^Writer this)
                 (when close-fn (close-fn))
                 nil)
          (write [str-cbuf off len]
            (when (pos? len)
              (if (instance? String str-cbuf)
                (.append sb ^String str-cbuf ^int off ^int len)
                (.append sb ^chars str-cbuf ^int off ^int len)))))
        java.io.BufferedWriter.
        java.io.PrintWriter.)))

(defn ^java.io.PrintWriter safe-terminal-writer [line-reader]
  (PrintWriter-on*
   (fn [^StringBuilder buffer]
     (let [s (.toString buffer)]
       (if (reading? line-reader)
         (let [last-newline (.lastIndexOf s "\n")]
           (when (>= last-newline 0)
             (let [substring-to-print (.substring s 0 (inc last-newline))]
               (.printAbove line-reader substring-to-print)
               (.delete buffer 0 (inc last-newline)))))
         (do
           (doto (.writer (.getTerminal line-reader))
             (.print s)
             (.flush))
           (.setLength buffer 0)))))
   nil))
