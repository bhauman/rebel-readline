# rebel-readline-cljs

[![Clojars Project](https://img.shields.io/clojars/v/com.bhauman/rebel-readline-cljs.svg)](https://clojars.org/com.bhauman/rebel-readline-cljs)

A library that supplies a rebel readline service for the default
clojurescript repl and some helpers to create CLJS repls with
rebel-readline.

## Quick try

Current `rebel-readline-cljs` uses ClojureScript 1.12.x and requires
Java 21 or newer. This project includes a `.java-version` file for
`jenv` users that selects Java 26.

#### Clojure tools

If you want to try this really quickly [install the Clojure CLI tools](https://clojure.org/guides/getting_started) and then invoke this:

```shell
clojure -Sdeps '{:deps {com.bhauman/rebel-readline-cljs {:mvn/version "0.1.8"}}}' -m rebel-readline.cljs.main
```

That should start a Node-backed ClojureScript REPL that takes its input
from the Rebel readline editor.

Note that I am using the `clojure` command and not the `clj` command
because the latter wraps the process with another readline program (`rlwrap`).

#### Leiningen

Add `[com.bhauman/rebel-readline-cljs "0.1.8"]` to the dependencies in your
`project.clj` then start a REPL like this:

```shell
lein trampoline run -m rebel-readline.cljs.main
```

#### Clone this repo

Clone this repo and then from the `rebel-readline-cljs` sub-directory
typing `clojure -M:dev -m rebel-readline.cljs.main` will get you into
a Clojure REPL with the readline editor working.

Node.js must be available on `PATH`.

## Usage

A simple usage example:

```clojure
(rebel-readline.core/with-line-reader
  (rebel-readline.clojure.core/create
    (rebel-readline.cljs.service.local/create))
  (cljs.repl/repl
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.cljs.repl/create-repl-read)))
```

## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
