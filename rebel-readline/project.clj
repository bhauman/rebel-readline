(defproject rebel-readline "0.1.0-SNAPSHOT"
  :description "Terminal readline library for Clojure dialects"
  :url "https://github.com/bhauman/rebel-readline"
  :license {:name "Eclipse Public License"
            :distribution :repo
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :scm {:name "git"
        :url "https://github.com/bhauman/rebel-readline"
        :dir ".."}
  
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.jline/jline-reader "3.5.1"]
                 [org.jline/jline-terminal "3.5.1"]                 
                 [cljfmt "0.5.7"]         ;; depends on tools reader
                 [compliment "0.3.5"]]

  :main rebel-readline.main

  :profiles {:dev {:source-paths ["src" "dev"]
                   :main rebel-dev.main}})
