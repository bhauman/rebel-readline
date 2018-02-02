(ns rebel-readline.jline-api
  (:require
   [clojure.string :as string]
   [rebel-readline.utils :refer [log]]
   [rebel-readline.service.core :as srv])
  (:import
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
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle
    #_InfoCmp$Capability]
   [org.jline.reader.impl DefaultParser BufferImpl]))

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

(defn attr-str [& args]
  (AttributedString.
   (reduce #(if %2
              (.append %1 %2)
              %1)
           (AttributedStringBuilder.) args)))

(defn attr-str-split [^AttributedString at-str regex]
  (let [s (str at-str)
        m (.matcher #"\n" s)]
    (->> (repeatedly #(when (.find m)
                        [(.start m) (.end m)]))
         (take-while some?)
         flatten
         (cons 0)
         (partition-all 2)
         (mapv
          #(.substring at-str (first %) (or (second %) (count s)))))))

(defn attr-partition-all [length ^AttributedString at-str]
  (mapv first
        (take-while some?
                    (rest
                     (iterate (fn [[_ at-str]]
                                (when at-str
                                  (if (<= (count at-str) length)
                                    [at-str nil]
                                    [(.substring at-str 0 (min length (count at-str)))
                                     (.substring at-str length (count at-str))])))
                              [nil at-str])))))

(defn attr-str-split-lines [^AttributedString at-str]
  (attr-str-split at-str #"\r?\n"))

(defn attr-str-join [sep coll]
  (apply attr-str (interpose sep coll)))

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

(defn bind-key [widget-id key-str]
  (doto *line-reader*
    (-> (.getKeyMaps)
        ;; TODO which map are we modifying here?
        (get "emacs")
        (.bind (org.jline.reader.Reference. widget-id) key-str))))

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
