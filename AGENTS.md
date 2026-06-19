# Project Notes for Agents

## Active Projects

This repository contains the active `rebel-readline`, `rebel-readline-cljs`, and
`rebel-readline-nrepl` projects.

The `rebel-readline-paredit` integration is not an active project. Do not spend
time updating or testing it unless a task explicitly asks for paredit work.

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
