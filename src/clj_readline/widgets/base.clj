(ns clj-readline.widgets.base
  (:require
   [clojure.repl]
   [clojure.string :as string]
   [clj-readline.parsing.tokenizer :as tokenize]
   [clj-readline.tools.indent :as indent]
   [clj-readline.tools.sexp :as sexp]
   [clj-readline.service.core :as srv]
   [clj-readline.tools.syntax-highlight :as highlight]
   [clj-readline.utils :refer [log]])
  (:use clj-readline.jline-api)
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.reader LineReader]
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle
    #_InfoCmp$Capability]
   [java.util.regex Pattern]))

;; TODO make services like source, document, and apropos abstract

(def indent-line-widget
  (create-widget
   (when (:indent (srv/config))
       (let [curs (cursor)
             s (buffer-as-string) ;; up-to-cursor better here?
             begin-of-line-pos   (sexp/search-for-line-start s (dec curs))
             leading-white-space (sexp/count-leading-white-space (subs s begin-of-line-pos))
         indent-amount       (#'indent/indent-amount s begin-of-line-pos)
         cursor-in-leading-white-space? (< curs
                                           (+ leading-white-space begin-of-line-pos))]
         
     (cursor begin-of-line-pos)
     (delete leading-white-space)
     (write  (apply str (repeat indent-amount \space)))
     
     ;; rectify cursor
     (when-not cursor-in-leading-white-space?
       (cursor (+ indent-amount (- curs leading-white-space))))))
   ;; return true to re-render
   true))

(def indent-or-complete-widget 
  (create-widget
    (let [curs (cursor)
          s (buffer-as-string) ;; up-to-cursor better here?
          begin-of-line-pos (sexp/search-for-line-start s (dec curs))
          leading-white-space (sexp/count-leading-white-space (subs s begin-of-line-pos))
          ;; indent-amount (#'ind/indent-amount s begin-of-line-pos)
          cursor-in-leading-white-space? (<= curs
                                             (+ leading-white-space begin-of-line-pos))]
      (if cursor-in-leading-white-space?
        (call-widget "indent-line")
        (call-widget LineReader/MENU_COMPLETE))
      true)))

;; ------------------------------------------------
;; Display argument docs on keypress functionality
;; ------------------------------------------------

(defn funcall-word [code-str open-paren-pos]
  (some->>
   (tokenize/tag-matches (subs code-str open-paren-pos)
                         ;; matches first word after paren
                         (Pattern/compile (str "(\\()\\s*(" tokenize/not-delimiter-exp "+)"))
                         :open-paren
                         :word)
   not-empty
   (take 2)
   ((fn [[a b]]
      (when (= a ["(" 0 1 :open-paren])
        b)))))

(defn one-space-after-funcall-word? []
  (let [s (buffer-as-string)
        curs (cursor)
        tagged-parses (tokenize/tag-sexp-traversal s)
        [_ start _ _] (sexp/find-open-sexp-start tagged-parses curs)]
    (when start
      (when-let [[word word-start word-end _]
                 (and start (= (.charAt s start) \()
                      (funcall-word s start))]
        (and (= (+ start word-end) curs)
             word)))))

;; TODO hard coded colors
(defn name-arglist-display [meta-data]
  (let [{:keys [ns doc arglists name]} meta-data]
    (when (and ns name)
      (let [x (doto (AttributedStringBuilder.)
                (.styled (.faint (.foreground AttributedStyle/DEFAULT 123))
                         (str ns))
                (.styled (.foreground AttributedStyle/DEFAULT 243) "/")
                (.styled (.faint (.foreground AttributedStyle/DEFAULT 178))
                         (str name)))]
        (when arglists
          (doto x
            (.styled (.foreground AttributedStyle/DEFAULT 243)
                     (str ": " (pr-str arglists)))))))))

(defn display-argument-help-message []
  (when-let [funcall-str (one-space-after-funcall-word?)]
    (when-let [fun-meta (srv/resolve-var-meta funcall-str)]
      (name-arglist-display fun-meta))))

;; TODO this ttd atom doesn't work really
;; need a global state and countdown solution
;; more than likely a callback on hook on key presses
(let [ttd-atom (atom -1)]
  (def self-insert-hook-widget
    "This hooks SELF_INSERT to capture most keypresses that get echoed
  out to the terminal. We are using it here to display interactive
  behavior based on the state of the buffer at the time of a keypress."
    (create-widget
     (when (zero? @ttd-atom)
       (display-message " "))
     (when-not (neg? @ttd-atom)
       (swap! ttd-atom dec))
     ;; hook here
     ;; if prev-char is a space and the char before that is part
     ;; of a word, and that word is a fn call
     (when (:eldoc (srv/config))
       (when-let [message (display-argument-help-message)]
         (reset! ttd-atom 1)
         (display-message message)))
     (call-widget LineReader/SELF_INSERT)
     true)))

;; --------------------------------
;; helpful partial tokenizer
;; --------------------------------

(defn word-at-position [p]
  (->> (tokenize/tag-words (str *buffer*))
       (filter #(= :word (last %)))
       (filter #(<= (second %) p (inc (nth % 2))))
       first))

(defn word-at-cursor []
  (word-at-position (cursor)))

;; -------------------------------------
;; Documentation widget
;; -------------------------------------


;; TODO trucated output should apply to everything that
;; displays a message
;; TODO need to set a default color
(defn truncated-message [length]
  (let [trunc-length (+ 2 (count "output truncated"))
        bar-length (/ (- length trunc-length) 2)
        bar (apply str (repeat bar-length \-))]
    (str bar " OUTPUT TRUNCATED " bar)))

;; TODO consider columns and wrapping as well
;; TODO need to set a default color
(defn shrink-multiline-to-terminal-size [s]
  (let [lines  (string/split-lines s)
        {:keys [rows cols]} (terminal-size)]
    (if (> (count lines) (+ rows 4))
      (->> lines 
           (take (- rows (min 4 rows)))
           vec
           (#(conj % (truncated-message cols)))
           (string/join (System/getProperty "line.separator")))
      s)))

(defn clojure-docs-url* [ns name]
  (when (.startsWith (str ns) "clojure.")
    (cond-> "https://clojuredocs.org/"
      ns (str ns)
      name (str "/" name))))

(defn clojure-docs-url [wrd]
  (when-let [{:keys [ns name]} (srv/resolve-var-meta wrd)]
    ;; TODO check if one of the available namespaces
    (when ns {:url (clojure-docs-url* (str ns) (str name))
              :ns (str ns)
              :name name})))

;; TODO hard coded colors
(defn doc-at-point []
  (when-let [[wrd] (word-at-cursor)]
    (when-let [{:keys [doc] :as var-meta-data} (srv/resolve-var-meta wrd)]
      (when doc
        (let [doc (shrink-multiline-to-terminal-size doc)]
          (if-let [name-line (name-arglist-display var-meta-data)]
            (do
              (when-let [url (:url (clojure-docs-url wrd))]
                (doto name-line
                  (.append (System/getProperty "line.separator"))
                  (.styled (.underline (.faint (.foreground AttributedStyle/DEFAULT 39)))
                           url)))
              (doto name-line
                (.append (System/getProperty "line.separator"))
                (.styled (.foreground AttributedStyle/DEFAULT 222)
                         doc)))
            doc))))))

(def document-at-point-widget
  (create-widget
   (when-let [doc (doc-at-point)]
     (display-message doc))
   true))

;; -------------------------------------
;; Source widget
;; -------------------------------------

(defn source-at-point []
  (when-let [[wrd] (word-at-cursor)]
    (when-let [{:keys [doc] :as var-meta-data} (srv/resolve-var-meta wrd)]
      (when-let [source (srv/source wrd)]
        (when-let [name-line (name-arglist-display var-meta-data)]
          (let [source (shrink-multiline-to-terminal-size source)]
            (when-not (string/blank? source)
              (doto name-line
                (.append (System/getProperty "line.separator"))
                (.append (highlight/highlight-clj-str source))))))))))

(def source-at-point-widget
  (create-widget
   (when-let [doc (source-at-point)]
     (display-message doc))
   true))

;; -------------------------------------
;; Apropos widget
;; -------------------------------------

;; most of the code below is for formatting the results into a set of columns
;; that fit the terminal

;; I experimented with creating url anchors that are supported by iTerm but
;; there seems to be a lack of support for arbitrary escape codes in Jline
(defn osc-hyper-link [url show]
  (str (char 27) "]8;;"  url (KeyMap/ctrl \G) show (char 27) "]8;;" (KeyMap/ctrl \G)))

;; TODO hard coded colors
(defn format-pair-to-width [wrd width [ns' name']]
  (let [sep (apply str (repeat (- width (count ns') (count name')) \space))
        idx (.indexOf name' wrd)
        doc-url (clojure-docs-url* ns' name')]
    (doto (AttributedStringBuilder.)
      (.append (subs name' 0 idx))
      (.styled (.foreground AttributedStyle/DEFAULT 45)
               (subs name' idx (+ idx (count wrd))))
      (.append (subs name' (+ idx (count wrd))))
      (.append sep)
      (.styled (.faint (.foreground AttributedStyle/DEFAULT 243)) ns'))))

(defn format-column [wrd column]
  (let [max-width (apply max (map count column))]
    (map (partial format-pair-to-width wrd max-width)
         (map #(string/split % #"\/") (sort column)))))

(defn row-width [columns]
  (+ (apply + (map #(apply max (map count %))
                   columns))
     (* 3 (dec (count columns)))))

;; stats

;; TODO move to lib
(defn mean [coll]
  (if (pos? (count coll))
    (/ (apply + coll)
       (count coll))
    0))

;; TODO move to lib
;; taken from clojure cookbook
(defn standard-deviation [coll]
  (let [avg (mean coll)
        squares (for [x coll]
                  (let [x-avg (- x avg)]
                    (* x-avg x-avg)))
        total (count coll)]
    (-> (/ (apply + squares)
           (- total 1))
        (Math/sqrt))))

;; TODO move to lib
(defn two-standards-plus-mean [coll]
  (+ (mean coll) (* 2 (standard-deviation coll))))

;; sometimes there are exceptinoally long namespaces and function
;; names these screw up formatting and are normally very disntant from
;; the thing we are looking for
(defn eliminate-long-outliers [coll]
  (let [max-len (two-standards-plus-mean (map count coll))]
    (filter #(< (count %) max-len) coll)))

(defn find-number-of-columns [coll total-width]
  (let [suggests coll]
    (first
     (filter
      #(let [columns (partition-all (Math/ceil (/ (count suggests) %)) suggests)]
         (< (row-width columns) total-width))
      (range 10 0 -1)))))

;; the operation for finding the optimal layout is expensive
;; but this is OK as we are going to do it upon request from the
;; user and not as the user is typing
(defn divide-into-displayable-columns [coll total-width]
  (let [max-length (apply max (map count coll))
        coll (eliminate-long-outliers coll)
        cols (find-number-of-columns coll total-width)]
    (cond
      (and (= cols 1) (< (count coll) 15))
      (list coll)
      (= cols 1)
      (recur (take (int (* 0.9 (count coll))) coll) total-width)
      (> (Math/ceil (/ (count coll) cols)) 20)
      (recur (take (int (* 0.9 (count coll))) coll) total-width)
      :else
      (map sort (partition-all (Math/ceil (/ (count coll) cols)) coll)))))

(defn formatted-apropos [wrd]
  (let [suggests (sort-by (juxt count identity)
                          (map str (srv/apropos wrd)))]
    (when-let [suggests (not-empty (take 50 suggests))]
      (let [terminal-width (:cols (terminal-size))
            max-length (apply max (map count suggests))]
        (->> (divide-into-displayable-columns suggests terminal-width)
             (map (partial format-column wrd))
             (apply map vector)
             (map #(interpose "   " %))
             (map #(apply attr-str %))
             (interpose (apply str (System/getProperty "line.separator")))
             (apply attr-str))))))

(def apropos-at-point-widget
  (create-widget
   (when-let [[wrd] (word-at-cursor)]
     (when-let [aprs (formatted-apropos wrd)]
       (display-message aprs)))
   true))

;; ------------------------------------------
;; In place eval widget
;; ------------------------------------------





;; --------------------------------------------
;; Base Widget registration and binding helpers
;; --------------------------------------------

(defn add-all-widgets [line-reader]
  (binding [*line-reader* line-reader]
    (register-widget "indent-line"        indent-line-widget)
    (register-widget "indent-or-complete" indent-or-complete-widget)
    (register-widget "self-insert-hook"   self-insert-hook-widget)
    (register-widget "doc-at-point"       document-at-point-widget)
    (register-widget "source-at-point"    source-at-point-widget)
    (register-widget "apropos-at-point"   apropos-at-point-widget)))

(defn add-default-bindings [line-reader]
  (binding [*line-reader* line-reader]
    (bind-key "indent-line"         (str (KeyMap/ctrl \X) (KeyMap/ctrl \I)))
    (bind-key "indent-or-complete"  (str #_(KeyMap/ctrl \X) (KeyMap/ctrl \I)))
    (bind-key "self-insert-hook"    (KeyMap/range " -~"))
    
    ;; the range behavior above overwrites all the bindings in the range
    ;; so this keeps the oringinal bracket matching behavior
    (bind-key LineReader/INSERT_CLOSE_PAREN ")")
    (bind-key LineReader/INSERT_CLOSE_SQUARE "]")
    (bind-key LineReader/INSERT_CLOSE_CURLY "}")
    
    (bind-key "doc-at-point"        (str (KeyMap/ctrl \X) (KeyMap/ctrl \D)))
    (bind-key "source-at-point"     (str (KeyMap/ctrl \X) (KeyMap/ctrl \S)))
    (bind-key "apropos-at-point"    (str (KeyMap/ctrl \X) (KeyMap/ctrl \A)))))

(defn add-default-widgets-and-bindings [line-reader]
  (-> line-reader
      add-all-widgets
      add-default-bindings))
