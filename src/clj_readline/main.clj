(ns clj-readline.main
  (:require
   [clj-readline.tools.read-forms :as forms]
   [clj-readline.line-reader :refer [line-reader] :as lr]
   [clj-readline.jline-api :as api]
   [clj-readline.io.line-print-writer :as line-print-writer]
   [clj-readline.service.impl.local-clojure-service :as local-clj-service]
   [clj-readline.service.core :as srv]
   [clj-readline.utils :refer [log]]
   [clojure.main]
   [clojure.string :as string])
  (:import
   [org.jline.reader
    UserInterruptException
    EndOfFileException])
  (:gen-class))

;; color isn't working for 256 color
(defn prompt []
  (with-out-str (clojure.main/repl-prompt))
  #_(let [sb (AttributedStringBuilder.)]
    (.styled sb (.foreground AttributedStyle/DEFAULT 33)
             (with-out-str (clojure.main/repl-prompt)))
    (.toAnsi sb)))

(defn repl-read [reader]
  (fn [request-prompt request-exit]
    (let [possible-forms (lr/read-line reader prompt request-prompt request-exit)]
      (if (#{request-prompt request-exit} possible-forms)
        possible-forms
        (when-not (string/blank? possible-forms)
          (with-in-str possible-forms
            (read {:read-cond :allow} *in*)))))))

(defn output-handler [reader]
  (fn [{:keys [text]}]
    (when (not (string/blank? text))
      (api/reader-println reader text))))

(defn repl [reader]
  (clojure.main/repl
   :prompt (fn [])
   :read (lr/clj-repl-read reader)
   :caught (fn [e]
             (cond (= (type e) EndOfFileException)
                   (System/exit 0)
                   (= (type e) UserInterruptException) nil
                   :else
                   ;; TODO work on error highlighting
                   (do
                     (log (Throwable->map e))
                     (clojure.main/repl-caught e)))))
  #_(binding [api/*line-reader* (:line-reader reader)
            srv/*service* (:service reader)]
    (let [out (line-print-writer/print-writer :out (output-handler (:line-reader reader)))]
      ;; you can do this to capture all thread output
      #_(alter-var-root #'*out* (fn [_] out))
      (binding [*out* out]
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
                           (clojure.main/repl-caught e)))))))

    )
  )

(defn new-repl []
  (lr/with-readline-input-stream (local-clj-service/create)
    (clojure.main/repl :prompt (fn []))))

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
  #_(new-repl)
  ;; also include prompt
  (let [reader (line-reader (local-clj-service/create))]
    (repl reader)
    #_(println ":::" (read-eval-loop reader))
    )
  

  
  )
