# rebel-readline

**WARNING: UNDER ACTIVE INITIAL DEVELOPMENT!!**

This is a pre-release. That is being made available for comment and behavior verification.

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

## Quick Usage

The main way to use this library is to replace the
`clojure.main/repl-read` behavior in `clojure.main/repl`. the
advantage of doing this is that it won't interfere with the input
stream if you are working on something that needs to read from
`*in*`. This is because the line-reader will only be engaged when the
repl loop is reading.

Example One:

```clojure
(clojure.main/repl
  :prompt (fn []) ;; prompt is handled by line-reader
  :read (rebel-readline.core/clj-repl-read
          (rebel-readline.core/line-reader
            (rebel-readline.service.impl.local-clojure-service/create))))
```

Example catching a bad terminal error and fall back to clojure.main/repl-read:

```clojure
(clojure.main/repl
  :prompt (fn [])
  :read (try
          (rebel-readline.core/clj-repl-read
           (rebel-readline.core/line-reader
             (rebel-readline.service.impl.local-clojure-service/create)))
          (catch clojure.lang.ExceptionInfo e
             (if (-> e ex-data :type (= :rebel-readline.line-reader/bad-terminal))
                (do (println (.getMessage e))
                    clojure.main/repl-read)
                (throw e)))))
```

Another option is to just wrap a call you your repl with
`rebel-readline.core/with-readline-input-stream` this will bind `*in*`
to an input-stream that is supplied by the line reader.

```clojure
(rebel-readline.core/with-readline-input-stream 
  (rebel-readline.service.impl.local-clojure-service/create)
    (clojure.main/repl :prompt (fn[])))
```

## Services

When you create a line reader with `rebel-readline.core/line-reader`
you need to supply a Service.

Services provide the link to the environment that does the work of
providing completion, documentation, source, apropos, eval and more
while the readline is actively reading.

You can see an example Service here:
https://github.com/bhauman/rebel-readline/blob/master/rebel-readline/src/rebel_readline/service/impl/local_clojure_service.clj

This environment doesn't strictly have to be the environment that the
readline results are being sent to for evaluation. Of course this
means readline functions like apropos, etc. will provide different results
than the actual evaluation env. But there are cases where this might
not be a problem at all.

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

## nREPL, SocketREPL, pREPL?

Services have not been written for these REPLs yet!! Things may change after intitial feedback.

But you can quickly implement a partial service in a fairly straight forward manner.

## Contributing

Please contribute!

I'm trying to mark issues with `help wanted` for issues that I feel
are good opportunities for folks to help out. If you want to work on
one of these please mention it in the issue.

If you do contribute:

* if the change isn't small please file an issue before a PR.
* please put all PR changes into one commit
* make small grokable changes. Large changes are more likely to be
  ingored and or used as a starting issue for exploration.
* break larger solutions down into a logical series of small PRs
* mention it at the start, if you are filing a PR that is more of an
  exploration of an idea

I'm going to be more open to repairing current behavior than I will be
to increasing the scope of rebel-readline.

I will have a preference for creating hooks so that additional functionality
can be layered on with libraries.

If you are wanting to contribute but don't know what to work on reach
out to me on the clojurians slack channel.

## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
