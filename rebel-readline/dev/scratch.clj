(in-ns 'rebel-readline.tools.sexp)

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

(comment

  (valid-sexp-from-point "      ((let [x y] (.asdf sdfsd))    " 10)
  
  (let [s "((let [x y] (.asdf sdfsd))  "
        tagged (tokenize/tag-sexp-traversal s)]
    (delimiter-boundaries tagged 15 15 true)
    )
  
  ;; current sexp boundaries
  ;; either begin and an end
  ;; or we close the current one
  
  (let [s "(let [x y] (.asdf sdfsd "
        tagged (tokenize/tag-sexp-traversal "(let [x y] (.asdf sdfsd ")]
    (closing-delimiter-for-parse tagged (count s) (count s))
    )
  
  )

(in-ns 'rebel-readline.line-reader)

;; ----------------------------------------
;; Completion context
;; ----------------------------------------

(defn parsed-line-word-coords [^ParsedLine parsed-line]
  (let [pos (.cursor parsed-line)
        word-cursor (.wordCursor parsed-line)
        word (.word parsed-line)
        word-start (- pos word-cursor)
        word-end (+ pos (- (count word) word-cursor))]
    [word-start word-end]))

(defn replace-word-with-prefix [parsed-line]
  (let [[start end] (parsed-line-word-coords parsed-line)
        line (.line parsed-line)]
    (str (subs line 0 start)
         "__prefix__"
         (subs line end (count line)))))

(defn complete-context [^ParsedLine parsed-line]
  (when-let [form-str (replace-word-with-prefix parsed-line)]
    (when-let [valid-sexp (sexp/valid-sexp-from-point form-str (.cursor parsed-line))]
      (binding [*default-data-reader-fn* identity]
        (try (read-string valid-sexp)
             (catch Throwable e
               (log :complete-context e)
               nil))))))



(comment
  (let [x (parsed-line (parse-line "((let [x y] (assoc x y 1)" 15))]
    (complete-context x)
    )




  )
