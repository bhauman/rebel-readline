(defproject rebel-readline "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.jline/jline "3.5.1"]
                 [cljfmt "0.5.7"]         ;; depends on tools reader
                 [compliment "0.3.5"]]

  ;  :aot :all
  
  :main rebel-readline.main

  :profiles {:dev {:source-paths ["src" "dev"]
                   :main rebel-dev.main}})
