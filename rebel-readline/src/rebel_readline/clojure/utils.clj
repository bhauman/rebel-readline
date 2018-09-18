(ns rebel-readline.clojure.utils
  (:require
   [clojure.string :as string]))

;; taken from cljs-api-gen.encode
(def cljs-api-encoding
  {"."  "DOT"
   ">"  "GT"
   "<"  "LT"
   "!"  "BANG"
   "?"  "QMARK"
   "*"  "STAR"
   "+"  "PLUS"
   "="  "EQ"
   "/"  "SLASH"})

;; taken from cljs-api-gen.encode
(defn cljs-api-encode-name [name-]
  (reduce (fn [s [a b]] (string/replace s a b))
    (name name-) cljs-api-encoding))

(defn url-for [ns name]
  (cond
    (.startsWith (str ns) "clojure.")
    (cond-> "https://clojuredocs.org/"
      ns (str ns)
      name (str "/" (string/replace name #"\?$" "_q")))
    (.startsWith (str ns) "cljs.")
    (cond-> "http://cljs.github.io/api/"
      ns (str ns)
      name (str "/" (cljs-api-encode-name name)))
    :else nil))
