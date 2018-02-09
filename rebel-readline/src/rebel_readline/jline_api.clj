(ns rebel-readline.jline-api
  (:require
   [clojure.string :as string]
   [rebel-readline.jline-api.attributed-string :as astring]
   [rebel-readline.service.core :as srv]
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
   [org.jline.reader.impl DefaultParser BufferImpl]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle
    #_InfoCmp$Capability]
   ))

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
(defmacro with-buffer [b & body]
  `(binding [rebel-readline.jline-api/*buffer* ~b
             rebel-readline.service.core/*service* (rebel-readline.service.impl.local-clojure-service/create)]
     ~@body))

(defmacro create-widget [& body]
  `(fn [line-reader#]
     (reify Widget
       (apply [_#]
         (binding [*line-reader* line-reader#
                   *buffer* (.getBuffer line-reader#)]
           ~@body)))))

;; very naive 
(def get-accessible-field
  (memoize (fn [obj field-name]
             (when-let [field (-> obj .getClass (.getDeclaredField field-name))]
               (doto field
                 (.setAccessible true))))))

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
  (get (key-maps) key-map-name))

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
  (.bind key-map (org.jline.reader.Reference. widget-id) key-str))

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

(defn set-right-prompt! [att-str]
  (let [right-prompt-field (get-accessible-field *line-reader* "rightPrompt")]
    (when att-str
      (.set right-prompt-field *line-reader* att-str))))

(defn register-widget [widget-id widget]
  (doto *line-reader*
    (-> (.getWidgets)
        (.put widget-id (if (fn? widget) (widget *line-reader*) widget)))))

(defn terminal-size []
  (let [size-field (get-accessible-field *line-reader* "size")]
    (when-let [sz (.get size-field *line-reader*)]
      {:rows (.getRows sz)
       :cols (.getColumns sz)})))

(defn redisplay []
  (.redisplay *line-reader*))

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
   (assert srv/*service* "Must have a service bound in order to print.")
   (binding [*line-reader* reader]
     (let [writer (.writer (.getTerminal reader))]
       (if (reading?)
         (do
           (.callWidget reader LineReader/CLEAR)
           (.println writer s)
           (.callWidget reader LineReader/REDRAW_LINE)
           (.callWidget reader LineReader/REDISPLAY)
           #_(.redisplay reader)
           (.flush writer)
           )
         (do
           (.println writer s)
           (.flush writer)))))))

#_(defn reader-println [reader s]
  (let [writer (.writer (.getTerminal reader))]
    (.callWidget reader LineReader/CLEAR)
    (.println writer s)
    (.callWidget reader LineReader/REDRAW_LINE)
    (.callWidget reader LineReader/REDISPLAY)
    (.flush writer)))

#_(defn output-tester [reader]
    (.start (Thread. (bound-fn []
                       (dotimes [n 20]
                         (Thread/sleep 1000)
                         (reader-println reader "WORLD!!"))))))
