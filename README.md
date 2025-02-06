# Rebel Readline

[![Clojars Project](https://img.shields.io/clojars/v/com.bhauman/rebel-readline.svg)](https://clojars.org/com.bhauman/rebel-readline)
[![Clojars Project](https://img.shields.io/clojars/v/com.bhauman/rebel-readline-cljs.svg)](https://clojars.org/com.bhauman/rebel-readline-cljs)
[![Clojars Project](https://img.shields.io/clojars/v/com.bhauman/rebel-readline-nrepl.svg)](https://clojars.org/com.bhauman/rebel-readline-nrepl)

Welcome to **Rebel Readline** – the snazzy terminal REPL for Clojure!

![asciicast](https://asciinema.org/a/160597.png)

## Features

Rebel Readline offers a Clojure REPL with:

- Easy multi-line editing
- Auto-indentation
- TAB completion
- Argument documentation displayed after typing a function name
- Inline evaluation, allowing you to evaluate code without pressing enter
- Quick access to documentation, source, and apropos for the symbol under the cursor
- Familiar terminal key bindings, including history search, etc.

Rebel Readline is also a library that provides a line reader for Clojure dialects.

## Purpose

Learn more about the motivations behind creating this terminal readline library [here](https://github.com/bhauman/rebel-readline/blob/master/rebel-readline/doc/intro.md).

## nREPL Support

Recent updates include nREPL support. For details, refer to [rebel-readline-nrepl](./rebel-readline-nrepl).

## Important Note

The line reader requires direct terminal access. Therefore, do not launch Rebel Readline using `clj` or any other readline processes (like `rlwrap`) to avoid conflicts. Use one of the following options to start the JVM:

- The java command
- The Clojure `clojure` tool (without readline support)
- `lein trampoline`
- `boot` (must run in Boot's worker pod)

## Quick Start

To quickly try Rebel Readline, [install the Clojure CLI tools](https://clojure.org/guides/getting_started) and execute:

```shell
clojure -Sdeps "{:deps {com.bhauman/rebel-readline {:mvn/version \"0.1.5\"}}}" -M -m rebel-readline.main
```

## Usage

### Key Bindings in the REPL

Rebel Readline defaults to Emacs-style key bindings, which can be configured. 

#### Notable Key Bindings:

- **Ctrl-C**: Abort the current line
- **Ctrl-D** at line start: Send an end-of-stream signal (usually quits the REPL)
- **TAB**: Word completion or code indentation 
- **Ctrl-X Ctrl-D**: Show documentation for the current symbol
- **Ctrl-X Ctrl-S**: Show source code for the current symbol
- **Ctrl-X Ctrl-A**: Show apropos information for the current symbol
- **Ctrl-X Ctrl-E**: Inline evaluation for SEXP

You can explore additional key bindings with the `:repl/key-bindings` command.

### Commands in the REPL

Commands start with the `:repl/...` keyword. For available commands, type `:repl/help` or `:repl` followed by TAB.

You can add new commands by implementing methods for the `rebel-readline.commands/command` multimethod and documenting them using `rebel-readline.commands/command-doc`.

## Installation

Add Rebel Readline as a tool within your `~/.clojure/deps.edn`:

```clojure
{
 ...
 :aliases {:rebel {:extra-deps {com.bhauman/rebel-readline {:mvn/version "0.1.5"}}
                   :exec-fn rebel-readline.tool/repl
                   :exec-args {}
                   :main-opts ["-m" "rebel-readline.main"]}}
 ...
}
```

You can then launch the REPL in your project directory with:

```shell
clojure -Xrebel 
```
Remember to use `clojure` instead of `clj` to avoid interference from other readline tools.

Alternatively, run it as a standalone tool:

```shell
clojure -T:rebel 
```

## CLI Parameters

You can pass [Configurable Parameters](#config) when launching the REPL:

```shell
clojure -Xrebel :highlight false 
```

It's also possible to specify parameters in the `:exec-args` key of your `~/.clojure/deps.edn`.

## CLI Usage with `rebel-readline.main`

You can also launch with the `rebel-readline.main` CLI. With the
configuration above you can use:

```shell
clojure -Mrebel --no-highlight
```

```shell
Options:
  -h, --help                                       Display help
  -k, --key-map KEYMAP         :emacs              Choose between :viins or :emacs
  -t, --color-theme THEME      :dark-screen-theme  :(light, dark, or neutral)-screen-theme
      --no-highlight                               Disable syntax highlighting
      --no-completion                              Disable code completion
      --no-eldoc                                   Disable function documentation display
      --no-indent                                  Disable auto indentation
      --no-redirect-output                         Disable output redirection
  -b, --key-bindings BINDINGS                      Specify custom key bindings
  -c, --config CONFIG                              Path to a config file
```

## Installing with Leiningen

Add the dependency to your `project.clj`:

```clojure
[com.bhauman/rebel-readline "0.1.5"]
```

Start the REPL with:

```shell
lein trampoline run -m rebel-readline.main
```

You can also add it to `$HOME/.lein/profiles.clj` so you don't have to
add it to individual projects.

To simplify REPL launches, create an alias in `project.clj`:

```clojure
:aliases {"rebl" ["trampoline" "run" "-m" "rebel-readline.main"]}
```

This lets you start the REPL using `lein rebl`.

## Boot Integration

Start Rebel Readline using Boot with:

```shell
boot -d com.bhauman/rebel-readline call -f rebel-readline.main/-main
```

## Default to vi Bindings

You can set vi key bindings either in your `deps.edn` or in `~/.clojure/rebel_readline.edn`:

```clojure
{:key-map :viins}
```

## Configuration

You can provide various configurable options in your `deps.edn` or `~/.clojure/rebel_readline.edn`:

```clojure
:config          - path to an edn configuration file

:key-map         - :viins or :emacs (default: :emacs)

:color-theme     - (:light, :dark or :neutral)-screen-theme

:highlight       - (boolean) enable syntax highlighting (default: true)

:completion      - (boolean) enable code completion (default: true)

:eldoc           - (boolean) enable function documentation display (default: true)

:indent          - (boolean) enable auto indentation (default: true)

:redirect-output - (boolean) rebinds output during read (default: true)

:key-bindings    - map of key bindings to apply after others
```

### Key Binding Configuration

To configure key bindings, use your configuration file. Ensure correct
serialization of key names.

## Using Rebel Readline as a Readline Library

Rebel Readline can replace the `clojure.main/repl-read` behavior:

```clojure
(rebel-readline.core/with-line-reader
  (rebel-readline.clojure.line-reader/create
    (rebel-readline.clojure.service.local/create {:highlight false}))
  (clojure.main/repl
     :prompt (fn []) ;; prompt is handled by line-reader
     :read (rebel-readline.clojure.main/create-repl-read)))
```

You can also use `rebel-readline.core/with-readline-in` for easier wrapping:

```clojure
(rebel-readline.core/with-readline-in
  (rebel-readline.clojure.line-reader/create
    (rebel-readline.clojure.service.local/create))
  (clojure.main/repl :prompt (fn [])))
```

## Services

The line reader provides capabilities like completion, documentation, and evaluation through a service. The common service is `rebel-readline.services.clojure.local`, which queries the local Clojure process.

For environments without a suitable service, you could use `clojure.service.local` or `clojure.service.simple`, though with less optimal results.

## CLJS Support

For ClojureScript, visit [this repository section](https://github.com/bhauman/rebel-readline/tree/master/rebel-readline-cljs).

## SocketREPL and pREPL Support

Currently, services for SocketREPL and pREPL are not available.

## Contributing

We welcome contributions! Look for issues marked `help wanted` for good starting points. 

When contributing:

- File an issue for non-trivial changes before creating a PR.
- Consolidate PR changes into one commit.
- Make changes small and easy to understand; this allows for better review.
- Break larger solutions into manageable PRs.
- Communicate if a PR represents more exploratory efforts.

If you need assistance on what to work on, feel free to reach out on the Clojurians Slack channel.

## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License, version 1.0 or (at your option) any later version.
