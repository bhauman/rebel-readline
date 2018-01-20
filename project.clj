(defproject clj-readline "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.jline/jline "3.5.1"]
                 #_[org.clojure/tools.reader "1.1.1"]
                 [cljfmt "0.5.7"]    ;; depends on tools reader
                 [compliment "0.3.5"] ;; has no dependencies other than clojure
                 #_[org.clojure/tools.nrepl "0.2.12"]
                 #_[cider/cider-nrepl "0.16.0-SNAPSHOT"]]

  
  
  :main clj-readline.main

  :profiles {:dev {:source-paths ["src" "dev"]}}
  
  )
