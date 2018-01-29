(ns clj-readline.service.core
  (:require
   [clj-readline.tools.colors :as colors]
   [clj-readline.tools.read-forms :as forms])
  (:import
   [org.jline.utils AttributedStyle]))

(def ^:dynamic *service* nil)

(defprotocol Config
  (-get-config [_ ])
  (-set-config! [_ v]))

(defprotocol Completions
  (-complete [_ word options]))

(defprotocol CurrentNs
  (-current-ns [_]))

(defprotocol ResolveVarMeta
  (-resolve-var-meta [_ var-str]))

(defprotocol ResolveNsMeta
  (-resolve-ns-meta [_ var-str]))

(defprotocol Source
  (-source [_ var-str]))

(defprotocol Apropos
  (-apropos [_ var-str]))

(defprotocol Document
  (-doc [_ var-str]))

(defprotocol AcceptLine
  (-accept-line [_ line cursor]))

(defprotocol ReadString
  (-read-string [_ str-form]))

(defprotocol Evaluation
  (-eval [_ form])
  (-eval-str [_ form-str]))

(def default-config
  {:indent true
   :eldoc true
   :highlight true
   :redirect-output true
   :color-theme :dark-screen-theme})

(defn config [] (-get-config *service*))

(defn apply-to-config [f & args]
  (when-let [res (apply f (config) args)]
    (-set-config! *service* res)))

(defn current-ns []
  (when (satisfies? CurrentNs *service*)
    (-current-ns *service*)))

(defn completions
  ([word]
   (completions word nil))
  ([word options]
   (when (satisfies? Completions *service*)
     (-complete *service* word options))))

(defn apropos [wrd]
  (when (satisfies? Apropos *service*)
    (-apropos *service* wrd)))

(defn doc [wrd]
  (when (satisfies? Document *service*)
    (-doc *service* wrd)))

(defn source [wrd]
  (when (satisfies? Source *service*)
    (-source *service* wrd)))

(defn resolve-var-meta [wrd]
  (when (satisfies? ResolveVarMeta *service*)
    (-resolve-var-meta *service* wrd)))

(defn resolve-ns-meta [wrd]
  (when (satisfies? ResolveNsMeta *service*)
    (-resolve-ns-meta *service* wrd)))

(defn accept-line [line-str cursor]
  (if (satisfies? AcceptLine *service*)
    (-accept-line *service* line-str cursor)
    (forms/default-accept-line line-str cursor)))

(defn read-form [form-str]
  (when (satisfies? ReadString *service*)
    (-read-string *service* form-str)))

(defn evaluate [form]
  (when (satisfies? Evaluation *service*)
    (-eval *service* form)))

(defn evaluate-str [form-str]
  (when (satisfies? Evaluation *service*)
   (-eval-str *service* form-str)))

(defn color [sk]
  (->
   (get (config) :color-theme)
   colors/color-themes 
   (get sk AttributedStyle/DEFAULT)))

#_(binding [*service* (clj-readline.service.impl.local-clojure-service/create)]
    (color :line-comment))
