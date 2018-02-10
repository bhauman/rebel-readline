# rebel-readline

**WARNING: UNDER ACTIVE INITIAL DEVELOPMENT!!**

This is a pre-release. That is being made available for comment and behavior verification.

[![Clojars Project](https://img.shields.io/clojars/v/rebel-readline.svg)](https://clojars.org/rebel-readline)
[![Clojars Project](https://img.shields.io/clojars/v/rebel-readline-cljs.svg)](https://clojars.org/rebel-readline-cljs)

A terminal readline library for Clojure Dialects

[![asciicast](https://asciinema.org/a/160597.png)](https://asciinema.org/a/160597)

## Important note!!! 

The line reader will attempt to manipulate the terminal that initiates
the JVM process. For this reason it is important to start your JVM in
a terminal.

That means you should launch your Java process using the

 * the java command
 * the Clojure `clojure` tool (without readline support)
 * lein trampoline 
 * boot - would need to run in boot's worker pod

Launching the terminal readline process from another Java process will not work.

It's best to not launch this readline behind other readline tools like `rlwrap`.

## Quick try

#### Clojure tools

If you want to try this really quickly
[install the Clojure CLI tools](https://clojure.org/guides/getting_started) 
and then invoke this:

`clojure -Sdeps "{:deps {rebel-readline {:mvn/version \"0.1.0-SNAPSHOT\"}}}" -m rebel-readline.main`

That should start a Clojure REPL that takes its input from the Rebel readline editor.

Note that I am using the `clojure` command and not the `clj` command
because the latter wraps the process with another readline program (rlwrap).

#### Clone repo

Clone this repo and then from the `rebel-readline` sub-directory
typing `lein trampoline run` will get you into a Clojure REPL with the
readline editor working.

Note that `lein run` will not work! See above.

## Quick Lay of the land

You should look at `rebel-readline.main` and `rebel-readline.core`
to give you top level usage information.

The core of the functionality is in `rebel-readline.line-reader` and
`rebel-readline.widgets.base` everything else is just support.

## Quick Usage

The main way to utililize this readline editor is to replace the
`clojure.main/repl-read` behavior in `clojure.main/repl`. 

The advantage of doing this is that it won't interfere with the input
stream if you are working on something that needs to read from
`*in*`. This is because the line-reader will only be engaged when the
REPL loop is reading.

Example:

```clojure
(clojure.main/repl
  :prompt (fn []) ;; prompt is handled by line-reader
  :read (rebel-readline.core/clj-repl-read
          (rebel-readline.core/line-reader
            (rebel-readline.service.impl.local-clojure-service/create))))
```

Another option is to just wrap a call you your REPL with
`rebel-readline.core/with-readline-input-stream` this will bind `*in*`
to an input-stream that is supplied by the line reader.

```clojure
(rebel-readline.core/with-readline-input-stream 
  (rebel-readline.service.impl.local-clojure-service/create)
    (clojure.main/repl :prompt (fn[])))
```

Or with a fallback:

```clojure
(try
  (rebel-readline.core/with-readline-input-stream 
    (rebel-readline.service.impl.local-clojure-service/create)
      (clojure.main/repl :prompt (fn[])))
  (catch clojure.lang.ExceptionInfo e
    (if (-> e ex-data :type (= :rebel-readline.line-reader/bad-terminal))
      (do (println (.getMessage e))
          (clojure.main/repl))
      (throw e))))
```

## Services

The line reader provides features like completion, documentation,
source, apropos, eval and more. The line reader needs a Service to
provide this functionality.

When you create a `rebel-readline.core/line-reader`
you need to supply this service.

The mose common service is the
`rebel-readline.services.impl.local-clojure-service` which uses the
local clojure process to provide this functionality and its a good
example of how a service works.

https://github.com/bhauman/rebel-readline/blob/master/rebel-readline/src/rebel_readline/service/impl/local_clojure_service.clj

In general, it's much better if the service is querying the Clojure process
where the eventual repl eval takes place.

However, the service doesn't necessarily have to query the same environment
that the REPL is using for evaluation. A great deal of functionality
can be supplied by the local clojure process if necessary. This could
be helpful when you have a Clojurey repl process and you don't have a
Service for it. In this case you can just use a
`local-clojure-service` or perhaps a simpler service. If you do this
you can expect less than optimal results but multiline editing, syntax
highlighting, auto indenting will all work.

If your service doesn't at least have an accurate
`rebel-readline.service.core/CurrentNs` implementation the readline
prompt will not correctly display the namespace.

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

Services have not been written for these REPLs yet!!

But you can quickly implement a partial service in a fairly straight
forward manner.

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
