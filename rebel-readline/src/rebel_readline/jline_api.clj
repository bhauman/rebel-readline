(ns rebel-readline.jline-api
  (:require
   [clojure.string :as string]
   [rebel-readline.jline-api.attributed-string :as astring]
   [rebel-readline.utils :refer [log]])
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
   [org.jline.reader.impl LineReaderImpl DefaultParser BufferImpl]
   [org.jline.terminal TerminalBuilder]
   [org.jline.terminal.impl DumbTerminal]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]))

(def ^:dynamic *terminal* nil)
(def ^:dynamic *line-reader* nil)
(def ^:dynamic *buffer* nil)

;; helper for development
(defn buffer*
  ([s] (buffer* s nil))
  ([s c]
   (doto (BufferImpl.)
     (.write s)
     (.cursor (or c (count s))))))

;; helper for development
#_(defmacro with-buffer [b & body]
  `(binding [rebel-readline.jline-api/*buffer* ~b
             rebel-readline.service/*service*
             (rebel-readline.service.local-clojure/create)]
     ~@body))

;; ----------------------------------------
;; Create a terminal
;; ----------------------------------------

(defn assert-system-terminal [terminal]
  (when (instance? DumbTerminal terminal)
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

(defmacro create-widget [& body]
  `(fn [line-reader#]
     (reify Widget
       (apply [_#]
         (binding [*line-reader* line-reader#
                   *buffer* (.getBuffer line-reader#)]
           (try
             ~@body
             (catch clojure.lang.ExceptionInfo e#
               (if-let [message# (.getMessage e#)]
                 (do (log :widget-execution-error (Throwable->map e#))
                     (display-message
                      (AttributedString.
                       message# (.foreground AttributedStyle/DEFAULT AttributedStyle/RED)))
                     true)
                 (throw e#)))))))))

;; very naive
(def get-accessible-field
  (memoize (fn [obj field-name]
             (or (when-let [field (-> obj
                                      .getClass
                                      .getSuperclass
                                      (.getDeclaredField field-name))]
                   (doto field
                     (.setAccessible true)))))))

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
       (map (fn [[k v]] [(KeyMap/display k) (.name v)]))
       (filter
        (fn [[k v]]
          (not
           (#{;; these don't exist for some reason
              "character-search"
              "character-search-backward"
              "infer-next-history"
              ;; these are too numerous
              ;; TODO would be nice to display
              ;; rolled up abbreviations
              "clojure-self-insert"
              "self-insert"
              "digit-argument"
              "do-lowercase-version"} v))))))

(defn key-maps []
  (-> *line-reader* (.getKeyMaps)))

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
  (get (.defaultKeyMaps *line-reader*) key-map-name))

(defn bind-key [key-map widget-id key-str]
  (when key-str
    (.bind key-map (org.jline.reader.Reference. widget-id) key-str)))

;; --------------------------------------
;; contextual ANSI
;; --------------------------------------

(defn ->ansi [at-str]
  (if-let [t (and *line-reader* (.getTerminal *line-reader*))]
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
  (doto *line-reader*
    (-> (.getWidgets)
        (.put widget-id (if (fn? widget) (widget *line-reader*) widget)))))

(defn terminal-size []
  (let [size-field (get-accessible-field *line-reader* "size")]
    (when-let [sz (.get size-field *line-reader*)]
      {:rows (.getRows sz)
       :cols (.getColumns sz)})))

(defonce redisplay-lock-obj (Object.))

(defn redisplay []
  (locking redisplay-lock-obj
    (.redisplay *line-reader*)))

(defn block-redisplay-millis [time-ms]
  (.start
   (Thread.
    (fn []
      (locking redisplay-lock-obj
        (Thread/sleep time-ms))))))

(defn display-message [message]
  (let [post-field (get-accessible-field *line-reader* "post")]
    (.set post-field *line-reader* (supplier (fn [] (AttributedString. message))))))

(defn rows-available-for-post-display []
  (let [rows (:rows (terminal-size))
        buffer-rows (count (string/split-lines (buffer-as-string)))]
    (max 0 (- rows buffer-rows))))

(defn reading? []
  (let [reading-field (get-accessible-field *line-reader* "reading")]
    (boolean (.get reading-field *line-reader*))))

(defn call-widget [widget-name]
  (.callWidget *line-reader* widget-name))

;; important
;; TODO make this work when reading? or not

(defn reader-println
  ([s] (reader-println *line-reader* s))
  ([reader s]
   ;; this function is normally called for concurrent output
   ;; there is a need to protect redisplay
   (locking redisplay-lock-obj
     (binding [*line-reader* reader]
       (let [writer (.writer (.getTerminal reader))]
         (if (reading?)
           (do
             (.callWidget reader LineReader/CLEAR)
             (.println writer s)
             (.callWidget reader LineReader/REDRAW_LINE)
             (.callWidget reader LineReader/REDISPLAY)
             (.flush writer))
           (do
             (.println writer s)
             (.flush writer))))))))

(defn create-line-reader [terminal app-name service]
  (let [service-variable-name (name :rebel-readline.service/service)
        swap* (fn [obj f args]
                (locking obj
                  (let [old-val @obj
                        new-val (apply f old-val args)]
                    (if (= @obj old-val)
                      (reset! obj new-val)
                      false))))]
    (proxy [LineReaderImpl clojure.lang.IDeref clojure.lang.IAtom]
        [terminal
         (or app-name "Rebel Readline")
         (java.util.HashMap. {(name ::service) service})]
      (deref [] (.getVariable this service-variable-name))
      ;; TODO implement all swaps??
      (swap  [f & args] (swap* this f args))
      (reset [a] (.setVariable this service-variable-name a)))))
