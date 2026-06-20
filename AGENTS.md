# Project Notes for Agents

## Active Projects

This repository contains the active `rebel-readline`, `rebel-readline-cljs`, and
`rebel-readline-nrepl` projects.

The `rebel-readline-paredit` integration is not an active project. Do not spend
time updating or testing it unless a task explicitly asks for paredit work.

## Repository Layout

* `rebel-readline` is the core terminal line reader and Clojure REPL project.
* `rebel-readline-cljs` adds the local ClojureScript REPL integration.
* `rebel-readline-nrepl` is the nREPL client integration.
* The sibling projects depend on the core `com.bhauman/rebel-readline`
  artifact for normal releases, but their `:dev` aliases override that
  dependency to the local `../rebel-readline` checkout.

When changing shared completion, line-reader, history, or JLine behavior, test
the relevant sibling project as well. Changes in `rebel-readline` can affect
both CLJS and nREPL.

## Versioning

Keep the active projects in sync for releases unless there is a specific reason
not to. Update these places together:

* `rebel-readline/deps.edn` `:neil` version
* `rebel-readline-cljs/deps.edn` dependency on `com.bhauman/rebel-readline` and
  its `:neil` version
* `rebel-readline-nrepl/deps.edn` dependency on `com.bhauman/rebel-readline` and
  its `:neil` version
* README examples that mention the released versions
* `rebel-readline/CHANGELOG.md`; update sibling changelogs when the sibling
  project has user-visible changes

## Deployment

Each deployable subproject uses the same deployment command from its own
directory:

```bash
clojure -T:build deploy
```

Run the command from the subproject being deployed, for example:

```bash
cd rebel-readline
clojure -T:build deploy
```

Clojars deployment requires `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` to be set
in the shell environment.

Deploy the core project first when sibling projects depend on the new core
version:

```bash
cd rebel-readline
clojure -T:build deploy

cd ../rebel-readline-cljs
clojure -T:build deploy

cd ../rebel-readline-nrepl
clojure -T:build deploy
```

After deploying, verify Clojars metadata if the release version matters:

```bash
curl -fsSL https://repo.clojars.org/com/bhauman/rebel-readline/maven-metadata.xml
curl -fsSL https://repo.clojars.org/com/bhauman/rebel-readline-cljs/maven-metadata.xml
curl -fsSL https://repo.clojars.org/com/bhauman/rebel-readline-nrepl/maven-metadata.xml
```

## Issue Workflow

Use the `gh` CLI from this checkout for GitHub issue triage and follow-up. When
fixes are released, comment on the issue with the released version and the
verification performed, then close the issue as completed.

## Local Files

It is normal for local tooling, generated output, or debugging artifacts to be
untracked in this checkout. Do not add those files unless the task explicitly
asks for them.
