(ns clj-readline.indenting
  (:require
   [clojure.string :as string]
   [cljfmt.core :refer [reformat-string]]))

#_(def rrr "12121\"asfasfd\"122\n1212\"asdf\\\"asdfa\"121\n2123123\"as\ndfasdf")

(def string-literal #"(\"[^\"\\]*(?:\\.[^\"\\]*)*\")")

(defn bounds-of-matches [s regex]
  (let [m (.matcher regex s)]
    (->> (iterate
          (fn [x]
            (when (.find m)
              [(.start m 1) (.end m 1)]))
          nil)
         (drop 1)
         (take-while some?))))

(def unterm-string-literal #"(\"[^\"\\]*(?:\\.[^\"\\]*)*)$")

#_(re-seq unterm-string-literal " \n \" \n  \"  ")

(defn in-quote? [s pos]
  (let [bounds (bounds-of-matches s string-literal)
        last-bound (-> bounds last second)]
    (if (and last-bound (< last-bound pos))
      ;; need to check for unterminated string literal
      (not-empty (re-seq unterm-string-literal (subs s last-bound pos)))
      (some #(<= (first %) pos (dec (second %))) bounds))))

(defn search-for-line-start [s pos]
  (loop [p pos]
    (cond
      (zero? p) p
      (= (.charAt s p) \newline)
      (inc p)
      :else (recur (dec p)))))

(defn search-for-quote-start [s pos]
  (loop [p pos]
    (cond
      (zero? p) p
      (and (= (.charAt s p) \")
           (not= (.charAt s (dec p)) \\))
      p
      :else (recur (dec p)))))

(def flip-delimiter {\} \{ \] \[ \) \(
                     \{ \} \[ \] \( \)})


;; have to ensure that we are not in a quote
;; or an uncompleted partial quote
(defn search-for-sexp-start
  ([s pos] (search-for-sexp-start s pos nil))
  ([s pos bracket]
   (loop [p pos]
     (cond
       (<= p 0) 0
       ;; ignore quotes
       (= \" (.charAt s p))
       (recur (dec (search-for-quote-start s (dec p))))

       (and bracket (= bracket (.charAt s p))) ;; found bracket
       p

       (and (not bracket) (#{\( \{ \[} (.charAt s p)))
       p
       
       (#{\} \] \)} (.charAt s p))
       (recur (dec (search-for-sexp-start s
                                          (dec p)
                                          (flip-delimiter
                                           (.charAt s p)))))
       :else (recur (dec p))))))

(defn indent-proxy-str [s cursor]
  (when-not (in-quote? s cursor)
    (let [sexp-start (search-for-sexp-start s (dec cursor))
          line-start (search-for-line-start s sexp-start)]
      (str (apply str (repeat (- sexp-start line-start) \space))
           (subs s sexp-start cursor)
           "\n1" (flip-delimiter (.charAt s sexp-start))))))

#_(defn count-leading-white-space [s] (count (take-while #{\space \t} s)))
(defn count-leading-white-space [s] (count (re-find #"^[^\S\n]+" s)))

;; TODO have to think more about indenting inside of maps
(defn indent-amount [s cursor]
  ;; TODO handle case where parse fails
  ;; by just grabbing the previous lines indent
  (if (zero? cursor)
    0
    (if-let [prx (indent-proxy-str s cursor)]
      (->> (try (reformat-string prx)
                (catch Exception e
                  ;; TODO this is temporary so that we can keep track of bad parses
                  (throw (ex-info "bad indenting parse" {:s s :cursor cursor :prx prx} e))))
           string/split-lines
           last
           count-leading-white-space)
      0)))

#_(defn remove-forward-white-space-at [s pos]
  (assert (<= pos (count s)))
  (let [blank-count (count-leading-white-space (subs s pos))]
    (str (subs s 0 pos) (subs s (+ pos blank-count) (count s)))))

#_(defn insert-at [s pos st]
  (str (subs s 0 pos) st (subs s pos (count s))))
