(defproject com.bhauman/rebel-readline "0.1.5-SNAPSHOT"
  :description "Terminal readline library for Clojure dialects"
  :url "https://github.com/bhauman/rebel-readline"
  :license {:name "Eclipse Public License"
            :distribution :repo
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :scm {:name "git"
        :url "https://github.com/bhauman/rebel-readline"
        :dir ".."}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.jline/jline-reader "3.5.1"]
                 [org.jline/jline-terminal "3.5.1"]
                 [org.jline/jline-terminal-jansi "3.5.1"]
                 [dev.weavejester/cljfmt "0.13.0"]     ;; depends on tools reader
                 [compliment/compliment "0.6.0"]]

  :profiles {:dev {:source-paths ["src" "dev"]
                   :main rebel-dev.main}})
