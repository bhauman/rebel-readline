(ns rebel-readline.commands
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :as string]
   [rebel-readline.jline-api :as api]
   [rebel-readline.jline-api.attributed-string :as astring]
   [rebel-readline.tools :as tools :refer [color]])
  (:import
   [org.jline.utils AttributedStringBuilder AttributedString AttributedStyle]
   [org.jline.reader LineReader EndOfFileException]))

(defmulti command first)
(defmulti command-doc identity)

(defmethod command :default [[com]]
  (println "No command" (pr-str com) "found."))

(defmethod command-doc :repl/toggle-indent [_]
  "Toggle the automatic indenting of Clojure code on and off.")

(defmethod command :repl/toggle-indent [_]
  (swap! api/*line-reader* update :indent #(not %))
  (if (:indent @api/*line-reader*)
    (println "Indenting on!")
    (println "Indenting off!")))

(defmethod command-doc :repl/toggle-highlight [_]
  "Toggle readline syntax highlighting on and off.  See
`:repl/toggle-color` if you want to turn color off completely.")

(defmethod command :repl/toggle-highlight [_]
  (swap! api/*line-reader* update :highlight #(not %))
  (if (:highlight @api/*line-reader*)
    (println "Highlighting on!")
    (println "Highlighting off!")))

(defmethod command-doc :repl/toggle-eldoc [_]
  "Toggle the auto display of function signatures on and off.")

(defmethod command :repl/toggle-eldoc [_]
  (swap! api/*line-reader* update :eldoc #(not %))
  (if (:eldoc @api/*line-reader*)
    (println "Eldoc on!")
    (println "Eldoc off!")))

(defmethod command-doc :repl/toggle-completion [_]
  "Toggle the completion functionality on and off.")

(defmethod command :repl/toggle-completion [_]
  (swap! api/*line-reader* update :completion #(not %))
  (if (:completion @api/*line-reader*)
    (println "Completion on!")
    (println "Completion off!")))

(defmethod command-doc :repl/toggle-color [_]
  "Toggle ANSI text coloration on and off.")

(defmethod command :repl/toggle-color [_]
  (let [{:keys [color-theme backup-color-theme]} @api/*line-reader*]
    (cond
      (and (nil? color-theme)
           (some? backup-color-theme)
           (tools/color-themes backup-color-theme))
      (do (println "Activating color, using theme: " backup-color-theme)
          (swap! api/*line-reader* assoc :color-theme backup-color-theme))
      (nil? color-theme)
      (do
        (swap! api/*line-reader* assoc :color-theme :dark-screen-theme)
        (println "Activating color, theme not found, defaulting to " :dark-screen-theme))
      (some? color-theme)
      (do
        (swap! api/*line-reader* assoc :backup-color-theme color-theme)
        (swap! api/*line-reader* dissoc :color-theme)
        (println "Color deactivated!")))))

(defmethod command-doc :repl/set-color-theme [_]
  (str "Change the color theme to one of the available themes:"
       "\n"
       (with-out-str
         (pprint (keys tools/color-themes)))))

(defmethod command :repl/set-color-theme [[_ new-theme]]
  (let [new-theme (keyword new-theme)]
    (if-not (tools/color-themes new-theme)
      (println
       (str (pr-str new-theme) " is not a known color theme, please choose one of:"
            "\n"
            (with-out-str
              (pprint (keys tools/color-themes)))))
      (swap! api/*line-reader* assoc :color-theme new-theme))))

(defmethod command-doc :repl/key-bindings [_]
  "With an argument displays a search of the current key bindings
Without any arguments displays all the current key bindings")

(defn readable-key-bindings [km]
  (map
   (fn [[k v]] [(if (= k (pr-str "^I"))
                  (pr-str "TAB")
                  k) v])
   km))

(defn classify-keybindings [km]
  (let [res (group-by (fn [[k v]]
                        (and (string? v)
                             (.startsWith v "clojure-"))) km)]
    (-> res
        (assoc :clojure (get res true))
        (assoc :other (get res false))
        (dissoc true false))))

(defn display-key-bindings [search & groups]
  (let [km (get (.getKeyMaps api/*line-reader*) "main")
        key-data (filter
                  (if search
                    (fn [[k v]]
                      (or (.contains k (name search))
                          (.contains v (name search))))
                    identity)
                  (api/key-map->display-data km))
        binding-groups (classify-keybindings (readable-key-bindings key-data))
        binding-groups (if (not-empty groups)
                         (select-keys binding-groups groups)
                         binding-groups)]
    (when-let [map-name (api/main-key-map-name)]
      (println (api/->ansi (astring/astr
                            ["Current key map: "         (.bold AttributedStyle/DEFAULT)]
                            [(pr-str (keyword map-name)) (color :font-lock/core-form)]))))
    (if (and search (empty? key-data))
      (println "Binding search: No bindings found that match" (pr-str (name search)))
      (doseq [[k data] binding-groups]
        (when (not-empty data)
          (println
           (api/->ansi
            (astring/astr
             [(format "%s key bindings:" (string/capitalize (name k)))
              (.bold AttributedStyle/DEFAULT)])))
          (println
           (string/join "\n"
                        (map (fn [[k v]]
                               (format "  %-12s%s" k v))
                             data))))))))

(defmethod command :repl/key-bindings [[_ search]]
  (display-key-bindings search))

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

(defn set-key-map! [key-map-name]
  (boolean
   (when (and key-map-name (api/set-main-key-map! (name key-map-name)))
     ;; its dicey to have parallel state like this so
     ;; we are just going to have the service config
     ;; state reflect the jline reader state
     (swap! api/*line-reader*
            assoc :key-map (keyword (api/main-key-map-name))))))

(defmethod command :repl/set-key-map [[_ key-map-name]]
  (if (set-key-map! key-map-name)
    (println "Changed key map to" (pr-str key-map-name))
    (println "Failed to change key map!")))

;; TODO this should be here the underlying repl should handle this
;; or consider a cross repl solution that works
;; maybe something you can put in service interface
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
  (display-key-bindings nil :clojure)
  (println
   (api/->ansi
    (apply
     astring/astr
     ["Available Commands:" (.bold AttributedStyle/DEFAULT)]
     "\n"
     (keep
      #(when-let [doc (command-doc %)]
         (astring/astr
          " "
          [(prn-str %) (color :widget/anchor)]
          (string/join
           "\n"
           (map (fn [x] (str "     " x))
                (string/split-lines doc)))
          "\n"))
      (sort (all-commands)))))))

(defn add-command [command-keyword action-fn doc]
  {:pre [(keyword? command-keyword)
         (fn? action-fn)
         (string? doc)]}
  (defmethod command command-keyword [_] (action-fn))
  (defmethod command-doc command-keyword [_] doc))
