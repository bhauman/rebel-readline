(ns clj-readline.tools.sexp
  (:require
   [clj-readline.parsing.tokenizer :as tokenize]))

(defn position-in-range? [s pos]
  (<= 0 pos (dec (count s))))

(defn blank-at-position? [s pos]
  (or (not (position-in-range? s pos))
      (Character/isWhitespace (.charAt s pos))))

(defn non-interp-bounds [code-str]
  (map rest
       (tokenize/tag-non-interp code-str)))

(defn in-non-interp-bounds? [code-str pos] ;; position of insertion not before
  (or (some #(and (< (first %) pos (second %)) %)
            (non-interp-bounds code-str))
      (and (<= 0 pos (dec (count code-str)))
           (= (.charAt code-str pos) \\)
           [pos (inc pos) :character])))

#_(tag-for-sexp-traversal " \"\" \\ ")

(def flip-it {:open-paren :close-paren
              :close-paren :open-paren
              :open-brace :close-brace
              :close-brace :open-brace
              :open-bracket :close-bracket
              :close-bracket :open-bracket})

(defn scan-builder [open-test close-test]
  (fn [specific-test stack x]
    (cond
      (open-test x)
      (cons x stack)
      (close-test x)
      (cond
        (and (empty? stack) (specific-test x))
        (reduced [:finished x])
        (empty? stack) (reduced [:finished nil]) ;; found closing bracket of wrong type
        (= (-> stack first last) (flip-it (last x)))
        (rest stack)
        ;; unbalanced
        :else (reduced [:finished nil]))
      :else stack)))

;; not used yet
(defn find-open-sexp-end [tagged-parses pos]
  (let [res (reduce
             (partial
              (scan-builder
               #(#{:open-bracket :open-brace :open-paren} (last %))
               #(#{:close-bracket :close-brace :close-paren} (last %)))
              identity)
             nil
             (drop-while
              #(<= (nth % 2) pos)
              tagged-parses))]
    (when (= :finished (first res))
      (second res))))

(defn find-open-sexp-start [tagged-parses pos]
  (let [res (reduce
             (partial
              (scan-builder
               #(#{:close-bracket :close-brace :close-paren} (last %))
               #(#{:open-bracket :open-brace :open-paren} (last %)))
              identity)
             nil
             (reverse (take-while
                       #(<= (nth % 2) pos)
                       tagged-parses)))]
    (when (= :finished (first res))
      (second res))))

#_(time (find-open-sexp-start (tag-for-sexp-traversal xxx) (dec (count xxx))))

(defn in-quote? [tagged-parses pos]
  (->> tagged-parses
       (filter #(#{:string-literal
                   :unterm-string-literal
                   :character
                   :end-line-comment} (last %)))
       (filter (fn [[_ start end _]]
                 (<= start pos (dec end))))
       first))

(defn search-for-line-start [s pos]
  (loop [p pos]
    (cond
      (<= p 0) 0
      (= (.charAt ^String s p) \newline)
      (inc p)
      :else (recur (dec p)))))

(def flip-delimiter-char {\} \{ \] \[ \) \(
                          \{ \} \[ \] \( \) \" \"})

(defn count-leading-white-space [s] (count (re-find #"^[^\S\n]+" s)))
