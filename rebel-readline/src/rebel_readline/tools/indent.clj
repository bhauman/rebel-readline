(ns rebel-readline.tools.indent
  (:require
   [clojure.string :as string]
   [rebel-readline.clojure.tokenizer :as tokenize]
   [rebel-readline.tools.sexp :as sexp]
   [cljfmt.core :refer [reformat-string]]))

(defn indent-proxy-str [s cursor]
  (let [tagged-parses (tokenize/tag-sexp-traversal s)]
    ;; never indent in quotes
    ;; this is an optimization, the code should work fine without this
    (when-not (sexp/in-quote? tagged-parses cursor)
      (when-let [[delim sexp-start] (sexp/find-open-sexp-start tagged-parses cursor)]
        (let [line-start (sexp/search-for-line-start s sexp-start)]
          (str (apply str (repeat (- sexp-start line-start) \space))
               (subs s sexp-start cursor)
               "\n1" (sexp/flip-delimiter-char (first delim))))))))

(defn indent-amount [s cursor]
  (if (zero? cursor)
    0
    (if-let [prx (indent-proxy-str s cursor)]
      (try (->>
            (reformat-string prx {:remove-trailing-whitespace? false
                                  :insert-missing-whitespace? false
                                  :remove-surrounding-whitespace? false
                                  :remove-consecutive-blank-lines? false})
            string/split-lines
            last
            sexp/count-leading-white-space)
           (catch clojure.lang.ExceptionInfo e
             (if (-> e ex-data :type (= :reader-exception))
               (+ 2 (sexp/count-leading-white-space prx))
               (throw e))))
      0)))
