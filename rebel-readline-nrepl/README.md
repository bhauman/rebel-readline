# rebel-readline-nrepl

> This is still a work in progress, the instructions below are pending
> as I haven't published the library to clojars yet

A Clojure library that provides a Rebel Readline nREPL client.

## Description
This project enables you to use the power and flexibility of Rebel
Readline in your terminal as an nREPL client. It leverages the
features of `rebel-readline` to enhance your REPL experience when
interacting with a remote nREPL server.

## Prerequisites
Before you start, ensure you have the following installed:

* [Clojure CLI tools](https://clojure.org/guides/install_clojure)

## Installation as a Clojure Tool

The easiest way to use `rebel-readline-nrepl` is by installing it as a
Clojure "tool":

```bash
# One-off installation of rebel-readline-nrepl as a tool:
clojure -Ttools install-latest :lib com.bhauman/rebel-readline-nrepl :as nrebel
```

##Usage

### Starting an nREPL server

To use rebel-readline-nrepl, you first need an nREPL server to connect
to. You can easily start a basic nREPL server with the following
command:

```bash
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.0.0"}}}' -M -m nrepl.cmdline --port 7888
```

This will start an nREPL server and print the port it's listening on.

### Connecting to the nREPL server

After installing `rebel-readline-nrepl` as a tool, you can connect to
an nREPL server using the following command:

```bash
# Replace 7888 with the port your nREPL server is listening on.
clojure -Tnrebel connect :host localhost :port 7888
```

Alternatively, if it's on your classpath (ie. in your deps.edn file)
you can invoke it directly using the clojure command:

```bash
clojure -m rebel-readline.nrepl.main --host localhost --port 7888
```

## Using with your project's deps.edn

If you want to include rebel-readline-nrepl in your project, add it as
a dependency in your deps.edn file:

```clojure
{:deps {com.bhauman/rebel-readline-nrepl {:mvn/version "NOT-PUBLISHED-YET"}}
 :aliases
 {:nrebel
  {:exec-fn rebel-readline.nrepl/connect
   :exec-args {:host "localhost" :port 7888}}}}
```

Which you can execute with 

```bash
clojure -X:nrebel 
```

## How do I default to Vi bindings?

To default to Vi key bindings, add the following to your
`~/.clojure/rebel_readline.edn` file:

```clojure
{:key-map :viins}
```

## Configuration
You can customize rebel-readline-nrepl by providing a configuration
map in `~/.clojure/rebel_readline.edn`. Here are the available options:

```clojure
{:key-map         :viins ; Either :viins or :emacs. Defaults to :emacs.
 :color-theme     :dark-screen-theme ; Either :light-screen-theme, :neutral-screen-theme or :dark-screen-theme.
 :highlight       true ; Boolean, whether to syntax highlight or not. Defaults to true.
 :completion      true ; Boolean, whether to complete on tab. Defaults to true.
 :eldoc           true ; Boolean, whether to display function docs as you type. Defaults to true.
 :indent          true ; Boolean, whether to auto indent code on newline. Defaults to true.
 :redirect-output true ; Boolean, rebinds root *out* during read to protect linereader. Defaults to true.
 :key-bindings    {}}   ; Map of key-bindings that get applied after all other key bindings have been applied.
```


#### Key binding config

You can further customize key bindings in the configuration file,
although this can be complex.

Example:

```clojure
{:key-bindings 
  {:emacs [["^D" :clojure-doc-at-point]] 
   :viins [["^J" :clojure-force-accept-line]]}}
```

**Note**: Serialized keybindings can be tricky. The keybinding strings are
translated using `org.jline.keymap.KeyMap/translate`, which has some
peculiarities.

If you need to use literal characters, you can represent them as a
list of characters or their corresponding integer values. For example,
instead of `"^D^D"`, you could use `(4 4)` (since `4` is the ASCII value for
`Ctrl-D`).

To find available widget names, use the `:repl/key-bindings` command at
the REPL prompt.

**Caveat**: JLine handles control characters and alphanumeric characters
well, but binding special characters might not always work as
expected.

## Key-bindings

Here are some useful keybindings

* **Ctrl-C**: Aborts editing the current line.
* **Ctrl-D** (at the start of a line): Sends an end-of-stream message, which typically quits the REPL.
* **TAB**: Word completion, or code indent if the cursor is at the beginning of a line within whitespace.
* **Ctrl-X Ctrl-D**: Shows documentation for the word at the cursor.
* **Ctrl-X Ctrl-S**: Shows the source code for the word at the cursor.
* **Ctrl-X Ctrl-A**: Shows apropos for the word at the cursor.
* **Ctrl-X Ctrl-E**: Inline evaluation of the S-expression before the cursor.

You can examine the key-bindings with the `:repl/key-bindings` command.

## Command System

`rebel-readline-nrepl` features a command system that enhances the
REPL experience.  If a line begins with a keyword in the `repl`
namespace (e.g., `:repl/command-name`), the line reader will interpret
it as a command.

**Discovering Available Commands:**

To see a list of available commands, type either `:repl/help` and
press Enter, or type `:repl` and press Tab for autocompletion
suggestions.


### License
Copyright © 2023 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.




