(ns clj-readline.widgets.base
  (:require
   [clojure.repl]
   [clojure.string :as string]
   [clojure.pprint]
   [clj-readline.parsing.tokenizer :as tokenize]
   [clj-readline.tools.indent :as indent]
   [clj-readline.tools.sexp :as sexp]
   [clj-readline.tools.colors :as col]   
   [clj-readline.service.core :as srv]
   [clj-readline.tools.syntax-highlight :as highlight]
   [clj-readline.utils :refer [log]])
  (:use clj-readline.jline-api)
  (:import
   [org.jline.keymap KeyMap]
   [org.jline.reader LineReader]
   [org.jline.utils AttributedStringBuilder AttributedString 
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

(defn name-arglist-display [meta-data]
  (let [{:keys [ns doc arglists name]} meta-data]
    (when (and ns name)
      (let [x (doto (AttributedStringBuilder.)
                (.styled (srv/color :eldoc-namespace) (str ns))
                (.styled (srv/color :eldoc-separator) "/")
                (.styled (srv/color :eldoc-varname) (str name)))]
        (when arglists
          (doto x
            (.styled (srv/color :eldoc-arglists)
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
(defn shrink-multiline-to-terminal-size
  ([s] (shrink-multiline-to-terminal-size s 0))
  ([s adjust]
   (let [lines  (string/split-lines s)
         {:keys [rows cols]} (terminal-size)
         rows (- rows (count (string/split-lines (buffer-as-string))))
         rows (+ rows adjust)]
     (if (and (pos? rows) (> (count lines) (+ rows 4)))
       (->> lines
            (take (- rows (min 4 rows)))
            vec
            (#(conj % (truncated-message cols)))
            (string/join (System/getProperty "line.separator")))
       s))))

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

(defn doc-at-point []
  (when-let [[wrd] (word-at-cursor)]
    (when-let [doc (srv/doc wrd)]
      (let [doc (shrink-multiline-to-terminal-size doc)
            sb (AttributedStringBuilder.)]
        (when-let [url (:url (clojure-docs-url wrd))]
          (doto sb
            (.styled (srv/color :light-anchor) url)
            (.append (System/getProperty "line.separator"))))
        (doto sb
          (.styled (srv/color :doc) doc))))))

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

(defn format-pair-to-width [wrd width [ns' name']]
  (let [sep (apply str (repeat (- width (count ns') (count name')) \space))
        idx (.indexOf name' wrd)]
    (doto (AttributedStringBuilder.)
      (.styled (srv/color :apropos-word) (subs name' 0 idx))
      (.styled (srv/color :apropos-highlight)
               (subs name' idx (+ idx (count wrd))))
      (.styled (srv/color :apropos-word) (subs name' (+ idx (count wrd))))
      (.append sep)
      (.styled (srv/color :apropos-namespace) ns'))))

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

(defn whitespace? [c]
  (re-matches #"[\s,]+" (str c)))

(defn scan-back-from [pred s pos]
  (first (filter #(pred (.charAt s %))
                 (range (min (dec (count s)) pos) -1 -1))))

(defn first-non-whitespace-char-backwards-from [s pos]
  (scan-back-from (complement whitespace?) s pos))

(defn sexp-ending-at-position [s pos]
  (let [c (try (.charAt s pos) (catch Exception e nil))]
    (when (#{ \" \) \} \] } c)
      (let [sexp-tokens (tokenize/tag-sexp-traversal s)]
        (or (when-let [res (sexp/in-quote? sexp-tokens pos)]
              res)
            (when-let [[_ start] (sexp/find-open-sexp-start sexp-tokens pos)]
              [(subs s start (inc pos)) start (inc pos) :sexp]))))))

(defn sexp-or-word-ending-at-position [s pos]
  (or (sexp-ending-at-position s pos)
      (word-at-position (inc pos))))

(defn in-place-eval []
  (let [s (buffer-as-string)
        pos (cursor)
        fnw-pos (first-non-whitespace-char-backwards-from s (dec pos))
        [form-str start end typ] (sexp-or-word-ending-at-position s fnw-pos)]
    (srv/evaluate-str form-str)))

(defn inline-result-marker [^AttributedString at-str]
  (attr-str
   (AttributedString. (str "#_=>")
                      (srv/color :inline-display-marker))
   " "
   at-str))

(defn limit-character-size [s]
  (let [{:keys [rows cols]} (terminal-size)
        max-char (int (* (- rows (count (string/split-lines (buffer-as-string))))
                         cols 0.5))]
    (if (< max-char (count s))
      (str (subs s 0 max-char) "...")
      s)))

(defn ensure-newline [s]
  (str (string/trim-newline s) (System/getProperty "line.separator")))

(defn format-data-eval-result [{:keys [out err result exception]}]
  (if exception
    (AttributedString. (str "=>!! " (:cause exception)) (srv/color :error))
    (cond-> (AttributedStringBuilder.)
      exception (.styled (srv/color :error) (str "=>!! " (:cause exception)) )
      (not (string/blank? out)) (.append (ensure-newline out)) ;; ensure newline
      (not (string/blank? err)) (.styled (srv/color :error) (ensure-newline err))
      ;; TODO truncate output
      result (.append
              (inline-result-marker
               (.toAttributedString
                (highlight/highlight-clj-str (binding [*print-length*
                                                       (min (or *print-length* Integer/MAX_VALUE)
                                                            100)
                                                       *print-level*
                                                       (min (or *print-level* Integer/MAX_VALUE)
                                                            5)]
                                               (limit-character-size (pr-str result))))))))))

(def eval-at-point-widget
  (create-widget
   (when-let [result (in-place-eval)]
     (display-message (format-data-eval-result result)))
   true))

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
    (register-widget "apropos-at-point"   apropos-at-point-widget)
    (register-widget "eval-at-point"      eval-at-point-widget)
    ))

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
    (bind-key "apropos-at-point"    (str (KeyMap/ctrl \X) (KeyMap/ctrl \A)))
    (bind-key "eval-at-point"       (str (KeyMap/ctrl \X) (KeyMap/ctrl \E)))))

(defn add-default-widgets-and-bindings [line-reader]
  (-> line-reader
      add-all-widgets
      add-default-bindings))
