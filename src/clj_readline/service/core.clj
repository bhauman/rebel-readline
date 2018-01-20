(ns clj-readline.service.core
  (:require
   [clj-readline.tools.read-forms :as forms]))

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
  (-eval [_ form]))

(defn config [] (-get-config *service*))

(defn apply-to-config [f & args]
  (when-let [res (apply f (config) args)]
    (-set-config! *service* res)))

(defn completions
  ([word]
   (completions word nil))
  ([word surrounding-sexp]
   (-complete *service* word surrounding-sexp)))

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
