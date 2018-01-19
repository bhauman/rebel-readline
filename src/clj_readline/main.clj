(ns clj-readline.main
  (:require
   [clj-readline.tools.read-forms :as forms]
   [clj-readline.line-reader :refer [line-reader] :as lr]
   [clj-readline.jline-api :as api]
   [clj-readline.io.line-print-writer :as line-print-writer]
   [clj-readline.service.impl.local-clojure-service :as local-clj-service]
   [clj-readline.utils :refer [log]]
   [clojure.tools.reader :as r]
   [clojure.tools.reader.reader-types :as rtyp]
   [clojure.main]
   [clojure.string :as string])
  (:import
   [org.jline.reader
    UserInterruptException
    EndOfFileException])
  (:gen-class))

#_(remove-ns 'clj-readline.core)

;; color isn't working for 256 color
(defn prompt []
  (with-out-str (clojure.main/repl-prompt))
  #_(let [sb (AttributedStringBuilder.)]
    (.styled sb (.foreground AttributedStyle/DEFAULT 33)
             (with-out-str (clojure.main/repl-prompt)))
    (.toAnsi sb)))

;; this is just a throwaway
;; for the demo
(defmulti repl-command first)

;; TODO add levenstein matching
(defmethod repl-command :default [[com]]
  (println "No command" (pr-str com) "found."))

(defmethod repl-command :repl/indent [_]
  (swap! api/*state* assoc :indent true)
  (println "Indenting on!"))

(defmethod repl-command :repl/highlight [_]
  (swap! api/*state* assoc :highlight true)
  (println "Highlighting on!"))

(defmethod repl-command :repl/eldoc [_]
  (swap! api/*state* assoc :eldoc true)
  (println "Eldoc on!"))

(defmethod repl-command :repl/quit [_]
  (println "Bye!")
  ;; request exit
  (System/exit 0)
  nil
  )

(defn get-command [forms]
  (when (some->> (first forms)
                 :read-value
                 (#(when (and (keyword? %)
                              (= (namespace %) "repl"))
                     %)))
    (keep :read-value forms)))

#_(defn repl-read [reader]
  (fn [request-prompt request-exit]
    (let [possible-forms (.readLine reader (prompt))
          possible-forms (forms/read-forms possible-forms)]
      (cond (empty? possible-forms)
            request-prompt
            (get-command possible-forms)
            (repl-command (get-command possible-forms))
            (first possible-forms)
            (r/read {:read-cond :allow} (rtyp/source-logging-push-back-reader
                                         (-> possible-forms first :source)))))))

(defn repl-read [reader]
  (fn [request-prompt request-exit]
    (let [possible-forms (lr/read-line reader prompt request-prompt request-exit)]
      (if (#{request-prompt request-exit} possible-forms)
        possible-forms
        (let [possible-forms (forms/read-forms possible-forms)]
          (when (first possible-forms)
            (r/read {:read-cond :allow} (rtyp/source-logging-push-back-reader
                                         (-> possible-forms first :source)))))))))

(defn output-handler [reader]
  (fn [{:keys [text]}]
    (when (not (string/blank? text))
      (api/reader-println reader text))))

(defn repl [reader]
  (let [out nil #_(line-print-writer/print-writer :out (output-handler reader))]
    ;; you can do this to capture all thread output
    #_(alter-var-root #'*out* (fn [_] out))
    (binding [api/*state* (atom {:indent true
                                 :highlight true
                                 :eldoc true})
              ;*out* out
              ]
      (clojure.main/repl
       :prompt (fn [])
       :read (repl-read reader)
       :caught (fn [e]
                 (cond (= (type e) EndOfFileException)
                       (System/exit 0)
                       (= (type e) UserInterruptException) nil
                       :else
                       ;; TODO work on error highlighting
                       (do
                         (log (Throwable->map e))
                         (clojure.main/repl-caught e))))))))

#_(def XXXX (line-reader))

#_(let [field (api/get-accessible-field XXXX "reading")]
    (type (.get field XXXX))
    (false? (.get field XXXX))
    (true? (.get field XXXX))
    (false? (boolean (.get field XXXX)))
    )

(defn -main []
  ;; for development
  #_(clojure.tools.nrepl.server/start-server :port 7888 :handler cider.nrepl/cider-nrepl-handler)

  ;; read all garbage before starting
  
  ;; also include prompt
  (let [reader (line-reader (local-clj-service/create {}))]
    (repl reader)
    #_(println ":::" (read-eval-loop reader))
    )
  

  
  )
