(ns rebel-readline.tools.syntax-highlight
  (:require
   [rebel-readline.service.core :as srv]
   [rebel-readline.parsing.tokenizer :as tokenize])
  (:import
   [org.jline.utils AttributedStringBuilder]))

(defn highlight-clj-str [syntax-str]
  (let [sb (AttributedStringBuilder.)]
    (loop [pos 0
           hd (tokenize/tag-syntax syntax-str)]
      (let [[_ start end sk] (first hd)]
        (cond
          (= (.length sb) (count syntax-str)) sb
          (= (-> hd first second) pos) ;; style active
          (do
            (if-let [st (srv/color sk)]
              (.styled sb st (subs syntax-str start end))
              (.append sb (subs syntax-str start end)))
            (recur end (rest hd)))
          ;; TODO this could be faster if we append whole sections
          ;; instead of advancing one char at a time
          ;; but its pretty fast now 
          :else
          (do (.append sb (.charAt syntax-str pos))
              (recur (inc pos) hd)))))))

#_ (time (highlight-clj-str code-str))
