(ns rebel-readline.tools.sexp
  (:require
   [rebel-readline.parsing.tokenizer :as tokenize]))

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

(def delim-key->delim
  {:open-paren \(
   :close-paren \)
   :open-brace \{
   :close-brace \}
   :open-bracket \[
   :close-bracket \]})

(def delim->delim-key
  (zipmap (keys delim-key->delim) (vals delim-key->delim)))

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
(defn find-open-sexp-end
  ([tagged-parses pos]
   (find-open-sexp-end tagged-parses pos identity))
  ([tagged-parses pos final-delim-pred]
   (let [res (reduce
              (partial
               (scan-builder
                #(#{:open-bracket :open-brace :open-paren} (last %))
                #(#{:close-bracket :close-brace :close-paren} (last %)))
               final-delim-pred)
              nil
              (drop-while
               #(<= (nth % 2) pos)
               tagged-parses))]
     (when (= :finished (first res))
       (second res)))))

(defn find-open-sexp-start
  ([tagged-parses pos]
   (find-open-sexp-start tagged-parses pos identity))
  ([tagged-parses pos final-delim-pred]
   (let [res (reduce
              (partial
               (scan-builder
                #(#{:close-bracket :close-brace :close-paren} (last %))
                #(#{:open-bracket :open-brace :open-paren} (last %)))
               final-delim-pred)
              nil
              (reverse (take-while
                        #(<= (nth % 2) pos)
                        tagged-parses)))]
     (when (= :finished (first res))
       (second res)))))

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

(defn closing-delimiter-for-parse [tagged-parses start-pos end-pos]
  (when-let [[delim start-pos' _ delim-key] (find-open-sexp-start tagged-parses start-pos)]
    (let [close-delim (flip-delimiter-char (first delim))]
      (cons [start-pos' end-pos delim-key :inserting-close]
            (closing-delimiter-for-parse
             (concat tagged-parses [(str close-delim) end-pos (inc end-pos) (flip-it delim-key)])
             start-pos'
             (inc end-pos))))))

(defn delimiter-boundaries
  ([tagged-parses start-pos end-pos]
   (delimiter-boundaries tagged-parses start-pos end-pos nil))
  ([tagged-parses start-pos end-pos fix]
  (when-let [[delim start-pos' _ delim-key] (find-open-sexp-start tagged-parses start-pos)]
    (if-let [[end-delim end-pos' _ end-delim-key]
               (find-open-sexp-end tagged-parses end-pos #(#{(flip-it delim-key)} (last %)))]
      (cons [start-pos' end-pos' delim-key]
            (delimiter-boundaries tagged-parses start-pos' (inc end-pos') fix))
      (when fix
        (closing-delimiter-for-parse
         (filter #(< (second %) end-pos) tagged-parses)
         start-pos
         end-pos))))))

(defn valid-sexp-from-point [s pos]
  (let [tagged (tokenize/tag-sexp-traversal s)]
    (when-let [boundaries (not-empty (delimiter-boundaries tagged pos pos true))]
      (if (some #(= (last %) :inserting-close) boundaries)
        (let [closes (filter #(= (last %) :inserting-close) boundaries)
              res (loop [[[start end open-delim-key] & cs] closes
                         s (subs s 0 (-> closes first second))]
                    (if-not start
                      s
                      (recur cs (str s (delim-key->delim (flip-it open-delim-key))))))]
          (subs res (-> closes last first) (count res)))
        (let [[start end] (last boundaries)]
          (subs s start (inc end)))))))

#_(valid-sexp-from-point "      ((let [x y] (.asdf sdfsd)    " 17)


