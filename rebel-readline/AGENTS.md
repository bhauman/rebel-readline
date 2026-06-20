# rebel-readline Agent Notes

This is the core library. Changes here can affect `rebel-readline-cljs` and
`rebel-readline-nrepl`, so test sibling projects when touching shared
line-reader behavior, completion, JLine integration, history handling, or
printing.

## Common Commands

Run the core test suite:

```bash
bb test:bb
```

Build the jar:

```bash
clojure -T:build jar
```

Run the local development REPL:

```bash
clojure -M:dev
```

Run Rebel Readline directly from this checkout:

```bash
clojure -M:rebel
```

## Release Notes

The release version lives in the `:aliases :neil :project :version` path in
`deps.edn`. For releases that include sibling projects, update the sibling
dependencies and deploy this project first.

## Terminal Behavior

Rebel Readline needs direct terminal access. Prefer `clojure`, not `clj`, when
launching it manually, and avoid wrapping it in `rlwrap`.

On Java 22 and newer, JLine can emit native access warnings. The documented
workaround is:

```bash
clojure -J--enable-native-access=ALL-UNNAMED -M:rebel
```
