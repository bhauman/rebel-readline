(ns rebel-readline.main
  (:require
   [rebel-readline.clojure.main :as main]
   [rebel-readline.tools :as tools]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [clojure.tools.cli :as cli]))

(def cli-options
  [["-h" "--help" "View cli help docs"]
   ["-k" "--key-map KEYMAP" "Either :viins or :emacs"
    :default :emacs
    :default-desc ":emacs"
    :parse-fn keyword
    :validate [#{:viins :emacs} "Must be either :viins or :emacs"]]
   ["-t" "--color-theme THEME" "Color theme :(light, dark, or neutral)-screen-theme"
    :default :dark-screen-theme
    :default-desc ":dark-screen-theme"
    :parse-fn keyword
    :validate [#{:light-screen-theme :dark-screen-theme :neutral-screen-theme}
               "Must be one of :light-screen-theme, :dark-screen-theme, or :neutral-screen-theme"]]
   #_[nil "--highlight" "Syntax highlighting"
    :default true]
   [nil "--no-highlight" "Disable syntax highlighting"
    :id :highlight
    :update-fn (constantly false)]
   #_[nil "--completion" "Enable code completion"
    :default true]
   [nil "--no-completion" "Disable code completion"
    :id :completion
    :update-fn (constantly false)]
   #_[nil "--eldoc" "Display function docs"
    :default true]
   [nil "--no-eldoc" "Disable function doc display"
    :id :eldoc
    :update-fn (constantly false)]
   #_[nil "--indent" "Auto indent code"
    :default true]
   [nil "--no-indent" "Disable auto indent"
    :id :indent
    :update-fn (constantly false)]
   #_[nil "--redirect-output" "Redirect output"
    :default true]
   [nil "--no-redirect-output" "Disable output redirection"
    :id :redirect-output
    :update-fn (constantly false)]
   ["-b" "--key-bindings BINDINGS" "Key bindings map"
    :parse-fn read-string
    :validate [map? "Must be a map"]]
   ["-c" "--config CONFIG" "Path to a config file"
    :parse-fn (comp str tools/absolutize-file)
    :validate [#(.exists (io/file %)) "Must be a valid path to a readable file"]]])

(defn usage [options-summary]
  (->> ["rebel-readline local: An enhanced terminal REPL for Clojure"
        ""
        "This is a readline enhanced REPL. Start your Clojure REPL with this"
        "to get a better editing experience.  See the full README and docs"
        "at https://github.com/bhauman/rebel-readline"
        ""
        "Usage: clojure -M -m rebel-readline.main [options]"
        ""
        "Options:"
        (string/join \newline
                     ;; removing default true noise from cli
                     (remove #(re-find #"\s\strue\s\s"  %)
                             (string/split-lines options-summary)))]
       (string/join \newline)))

(cli/parse-opts ["--no-highlight"]  cli-options :no-defaults true)

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join "\n" errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options :no-defaults true)]
    (cond
      (:help options)
      (exit 0 (usage summary))
      errors
      (exit 1 (error-msg errors))
      :else options)))

(defn -main [& args]
  (let [options (validate-args args)]
    (main/main {:rebel-readline/config options})))
