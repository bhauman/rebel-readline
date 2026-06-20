# rebel-readline-cljs Agent Notes

This project provides the ClojureScript integration for Rebel Readline. It
depends on `com.bhauman/rebel-readline` for releases and uses a local
`../rebel-readline` override in the `:dev` alias.

## Runtime Expectations

Current development targets ClojureScript 1.12.x with Java 21 or newer. This
project has a `.java-version` selecting Java 26 for `jenv` users. Use
`jenv exec` for local commands when multiple Java runtimes are installed.

Node.js must be available on `PATH`; the dev entrypoint is Node-backed.

## Common Commands

Run the local CLJS Rebel Readline REPL:

```bash
jenv exec clojure -M:dev -m rebel-readline.cljs.main
```

Run the focused CLJS service test:

```bash
jenv exec clojure -Sdeps '{:deps {io.github.cognitect-labs/test-runner {:git/tag "v0.5.1" :git/sha "dfb30dd"}} :aliases {:test-runner {:extra-paths ["test"] :main-opts ["-m" "cognitect.test-runner"]}}}' -M:dev:test-runner -n rebel-readline.cljs.service.local-test
```

Build the jar:

```bash
jenv exec clojure -T:build jar
```

## Completion Notes

CLJS completion depends on passing the current namespace as `:context-ns` to
`cljs-tooling.complete/completions`. Alias candidates may be symbols; the core
line-reader must coerce candidates to strings before constructing JLine
`Candidate` objects.
