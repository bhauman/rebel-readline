(ns rebel-readline.commands
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [rebel-readline.jline-api :as api]
   [rebel-readline.jline-api.attributed-string :as astring]   
   [rebel-readline.tools.syntax-highlight :as syn]
   [rebel-readline.tools.colors :as col]
   [rebel-readline.service.core :as srv])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]
   [org.jline.reader LineReader EndOfFileException]))

(defmulti command first)
(defmulti command-doc identity)

(defmethod command :default [[com]]
  (println "No command" (pr-str com) "found."))

(defmethod command-doc :repl/toggle-indent [_]
  "Toggle auto indenting on and off.")

(defmethod command :repl/toggle-indent [_]
  (srv/apply-to-config update :indent #(not %))
  (if (:indent (srv/config))
    (println "Indenting on!")
    (println "Indenting off!")))

(defmethod command-doc :repl/toggle-highlight [_]
  "Toggle readline syntax highlighting on and off.  See
`:repl/toggle-color` if you want to turn color off completely.")

(defmethod command :repl/toggle-highlight [_]
  (srv/apply-to-config update :highlight #(not %))
  (if (:highlight (srv/config))
    (println "Highlighting on!")
    (println "Highlighting off!")))

(defmethod command-doc :repl/toggle-eldoc [_]
  "Toggle the auto display of function signatures on and off.")

(defmethod command :repl/toggle-eldoc [_]
  (srv/apply-to-config update :eldoc #(not %))
  (if (:eldoc (srv/config))
    (println "Eldoc on!")
    (println "Eldoc off!")))

(defmethod command-doc :repl/toggle-completion [_]
  "Toggle the completion functionality on and off.")

(defmethod command :repl/toggle-completion [_]
  (srv/apply-to-config update :completion #(not %))
  (if (:completion (srv/config))
    (println "Completion on!")
    (println "Completion off!")))

(defmethod command-doc :repl/toggle-color [_]
  "Toggle ANSI text coloration on and off.")

(defmethod command :repl/toggle-color [_]
  (let [{:keys [color-theme backup-color-theme]}
        (srv/config)]
    (cond
      (and (nil? color-theme)
           (some? backup-color-theme)
           (col/color-themes backup-color-theme))
      (srv/apply-to-config assoc :color-theme backup-color-theme)
      (nil? color-theme)
      (srv/apply-to-config assoc :color-theme :dark-screen-theme)
      (some? color-theme)
      (do
        (srv/apply-to-config assoc :backup-color-theme color-theme)
        (srv/apply-to-config dissoc :color-theme)))))

(defmethod command-doc :repl/set-color-theme [_]
  (str "Change the color theme to one of the available themes:"
       (System/getProperty "line.separator")
       (with-out-str
         (pprint (keys col/color-themes)))))

(defmethod command :repl/set-color-theme [[_ new-theme]]
  (let [new-theme (keyword new-theme)]
    (if-not (col/color-themes new-theme)
      (println
       (str (pr-str new-theme) " is not a known color theme, please choose one of:"
            (System/getProperty "line.separator")
            (with-out-str
              (pprint (keys col/color-themes)))))
      (srv/apply-to-config assoc :color-theme new-theme))))

(defmethod command-doc :repl/key-bindings [_]
  "With an argument displays a search of the current key bindings
Without any arguments displays all the current key bindings")

(defmethod command :repl/key-bindings [[_ search]]
  (let [km (get (.getKeyMaps api/*line-reader*) "main")
        key-data (filter
                  (if search
                    (fn [[k v]]
                      (or (.contains k (name search))
                          (.contains v (name search))))
                    identity)
                  (api/key-map->display-data km))]
    (when-let [map-name (api/main-key-map-name)]
      (println "Current key map:" (keyword map-name)))
    (if (and search (empty? key-data))
      (println "Binding search: No bindings found that match" (pr-str (name search)))
      (println
       (string/join (System/getProperty "line.separator")
                    (map (fn [[k v]]
                           (format "  %-12s%s" k v))
                         key-data))))))

(defmethod command-doc :repl/set-key-map [_]
  (let [map-names (->> (api/key-maps)
                       keys
                       (filter (complement #{".safe"
                                             "visual"
                                             "main"
                                             "menu"
                                             "viopp"}))
                       sort
                       (map keyword)
                       pr-str)]
    (str "Changes the key bindings to the given key-map. Choose from: "
         map-names)))

(defmethod command :repl/set-key-map [[_ key-map-name]]
  (if (and key-map-name (api/set-main-key-map! (name key-map-name)))
    (println "Changed key map to" (pr-str key-map-name))
    (println "Failed to change key map!")))

;; TODO this should be here the underlying repl should handle this
;; or consider a cross repl solution that works
;; maybe something you can put in service core interface
(defmethod command-doc :repl/quit [_]
  "Quits the REPL. This may only work in certain contexts.")

(defmethod command :repl/quit [_]
  (println "Bye!")
  ;; request exit
  (throw (EndOfFileException.)))

(defn handle-command [command-str]
  (let [cmd? 
        (try (read-string (str "[" command-str "]"))
             (catch Throwable e
               []))]
    (if (and (keyword? (first cmd?))
             (= "repl" (namespace (first cmd?))))
      (do (command cmd?) true)
      false)))

(defn all-commands []
  (filter #(= (namespace %) "repl")
   (keys (.getMethodTable command))))

(defmethod command-doc :repl/help [_]
  "Prints the documentation for all available commands.")

(defmethod command :repl/help [_]
  (println
   (.toAnsi
    (apply
     astring/astr
     ["Available Commands:" (.bold AttributedStyle/DEFAULT)]
     (System/getProperty "line.separator")
     (keep
      #(when-let [doc (command-doc %)]
         (astring/astr
          " "
          [(prn-str %) (.underline (col/fg-color AttributedStyle/CYAN))]
          (string/join
           (System/getProperty "line.separator")
           (map (fn [x] (str "     " x))
                (string/split-lines doc)))
          (System/getProperty "line.separator")))
      (sort (all-commands)))))))

#_ (require 'rebel-readline.service.impl.local-clojure-service)
#_(binding [srv/*service* (rebel-readline.service.impl.local-clojure-service/create)]
    (handle-command ":repl/toggle-ind")
    (handle-command ":repl/toggle-indent")
    (srv/config)
    
  )
