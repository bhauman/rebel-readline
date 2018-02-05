(defproject rebel-readline-cljs "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[rebel-readline "0.1.0-SNAPSHOT"]
                 [org.clojure/clojurescript "1.9.946"]
                 [cljs-tooling "0.2.0"]]

  :main rebel-readline-cljs.main

  :profiles {:dev {:source-paths ["src" "../rebel-readline/src"]}})

