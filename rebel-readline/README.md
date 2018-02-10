# rebel-readline

**WARNING: UNDER ACTIVE INITIAL DEVELOPMENT!!**

[![Clojars Project](https://img.shields.io/clojars/v/rebel-readline.svg)](https://clojars.org/rebel-readline)
[![Clojars Project](https://img.shields.io/clojars/v/rebel-readline-cljs.svg)](https://clojars.org/rebel-readline-cljs)

A terminal readline library for Clojure Dialects

[![asciicast](https://asciinema.org/a/160597.png)](https://asciinema.org/a/160597)

rebel-readline will undoubtedly lead to a "rebel-repl" of some kind ...

## Important note!!! 

The line reader will attempt to manipulate the terminal that initiates
the JVM process. For this reason it is important to start your JVM in
a terminal.

That means you launch your java process using the

 * the java command
 * the Clojure clj tool
 * lein trampoline 
 * boot - would need to run in boot's worker pod

Launching the terminal readline process from another java process will not work.

## Quick try

`lein trampoline run` will get you into a clojure repl with the readline working.

## Quick Lay of the land

You should look at `rebel-readline.main` and `rebel-readline.core`
to give you top level usage information.

The meat of the functionality is in `rebel-readline.line-reader` and
`rebel-readline.widgets.base` everything else is just support.

## Keybindings

**Bindings of interest**

* Ctrl-C => aborts editing the current line
* Ctrl-D at the start of a line => sends an end of stream message
  which in most cases should quit the REPL

* TAB => word completion or code indent if the cursor is in the whitespace at the
  start of a line
* Ctrl-X_Ctrl-D => Show documentation for word at point
* Ctrl-X_Ctrl-S => Show source for word at point
* Ctrl-X_Ctrl-A => Show apropos for word at point
* Ctrl-X_Ctrl-E => Inline eval for SEXP before the point

You can examine the keybindings with the `:repl/key-bindings` command.

## Commands

There is a command system. If the line starts with a "repl" namespaced
keyword then the line-reader will attempt to interpret it as a command.

Type `:repl/help` or `:repl` TAB to see a list of available commands.

You can add new commands by adding methods to the
`rebel-readline.commands/command` multimethod. You can add
documentation for the command by adding a method to the
`rebel-readline.commands/command-doc` multimethod.

## CLJS

See https://github.com/bhauman/rebel-readline/tree/master/rebel-readline-cljs

## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
