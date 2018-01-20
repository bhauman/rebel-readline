(ns clj-readline.commands
  (:require
   [clj-readline.service.core :as srv]))

(defmulti command first)

(defmethod command :default [[com]]
  (println "No command" (pr-str com) "found."))

(defmethod command :repl/toggle-indent [_]
  (srv/apply-to-config update :indent #(not %))
  (if (:indent (srv/config))
    (println "Indenting on!")
    (println "Indenting off!")))

(defmethod command :repl/toggle-highlight [_]
  (srv/apply-to-config update :highlight #(not %))
  (if (:highlight (srv/config))
    (println "Highlighting on!")
    (println "Highlighting off!")))

(defmethod command :repl/toggle-eldoc [_]
  (srv/apply-to-config update :eldoc #(not %))
  (if (:eldoc (srv/config))
    (println "Eldoc on!")
    (println "Eldoc off!")))

(defmethod command :repl/quit [_]
  (println "Bye!")
  ;; request exit
  (throw (ex-info "Exit Request" {:request-exit true})))

(defn handle-command [command-str]
  (let [cmd? 
        (try (read-string (str "[" command-str "]"))
             (catch Throwable e
               []))]
    (if (and (keyword? (first cmd?))
             (= "repl" (namespace (first cmd?))))
      (do (command cmd?) true)
      false)))

#_(binding [srv/*service* (clj-readline.service.impl.local-clojure-service/create)]
    (handle-command ":repl/toggle-ind")
    (handle-command ":repl/toggle-indent")
    (srv/config)
    
  )
