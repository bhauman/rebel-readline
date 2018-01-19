(ns clj-readline.tools.indent
  (:require
   [clojure.string :as string]
   [clj-readline.parsing.tokenizer :as tokenize]
   [clj-readline.tools.sexp :as sexp]
   [cljfmt.core :refer [reformat-string]]))

(defn indent-proxy-str [s cursor]
  (let [tagged-parses (tokenize/tag-sexp-traversal s)]
    (when-not (sexp/in-quote? tagged-parses cursor)
      (when-let [[delim sexp-start] (sexp/find-open-sexp-start tagged-parses (dec cursor))]
        (let [line-start (sexp/search-for-line-start s sexp-start)]
          (str (apply str (repeat (- sexp-start line-start) \space))
               (subs s sexp-start cursor)
               "\n1" (sexp/flip-delimiter-char (first delim))))))))

(defn indent-amount [s cursor]
  ;; TODO handle case where parse fails
  (if (zero? cursor)
    0
    (if-let [prx (indent-proxy-str s cursor)]
      (->> (try (reformat-string prx {:remove-trailing-whitespace? false
                                      :insert-missing-whitespace? false
                                      :remove-surrounding-whitespace? false
                                      :remove-consecutive-blank-lines? false})
                (catch Exception e
                  ;; this is the fallback for indenting 
                  #_(count-leading-white-space prx)
                  ;; TODO this is temporary so that we can keep track of bad parses
                  (throw (ex-info "bad indenting parse" {:s s :cursor cursor :prx prx} e))))
           string/split-lines
           last
           sexp/count-leading-white-space)
      0)))
