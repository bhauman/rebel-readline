# rebel-readline-nrepl Agent Notes

This project provides the nREPL client integration for Rebel Readline. It
depends on `com.bhauman/rebel-readline` for releases and uses a local
`../rebel-readline` override in the `:dev` alias.

## Common Commands

Start a basic Clojure nREPL server:

```bash
clojure -M:nrepl
```

Connect the Rebel Readline nREPL client to a server:

```bash
clojure -M:dev -m rebel-readline.nrepl.main --port 50668
```

Smoke-load the client namespaces:

```bash
clojure -M:dev -e '(require (quote rebel-readline.nrepl.main) (quote rebel-readline.nrepl.service.nrepl)) (println :nrepl-ok)'
```

## Babashka nREPL

Babashka nREPL compatibility can be checked with:

```bash
bb --nrepl-server localhost:16670
clojure -M:dev -m rebel-readline.nrepl.main --port 16670 --no-background-print
```

A direct service-layer smoke test is useful when terminal interaction is hard to
capture:

```bash
clojure -M:dev -e '(require (quote [rebel-readline.nrepl.service.nrepl :as svc])) (let [s (svc/create {:host "localhost" :port 16670})] (svc/start-polling s) (try (println :eval (pr-str (svc/tool-eval-code s "(+ 1 2)"))) (println :lookup (pr-str (select-keys (svc/lookup s "map") [:ns :name :arglists :doc]))) (println :completions (pr-str (take 5 (or (svc/completions s "ma") [])))) (finally (svc/stop-polling s))))'
```

Expected behavior with current Babashka: eval, lookup/doc data, and completions
should all return useful responses.

## Release Notes

Deploy `rebel-readline` first when this project depends on a new core version,
then deploy this project with:

```bash
clojure -T:build deploy
```
