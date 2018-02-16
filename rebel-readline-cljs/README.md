# rebel-readline-cljs

A library that supplies a rebel readline service for the default
clojurescript repl and a `cljs.repl/repl-read` replacement.

## Quick try

#### Clojure tools

If you want to try this really quickly [install the Clojure CLI tools](https://clojure.org/guides/getting_started) and then invoke this:

`clojure -Sdeps "{:deps {rebel-readline-cljs {:mvn/version \"0.1.0-SNAPSHOT\"}}}" -m rebel-readline-cljs.main`

That should start a Nashorn ClojureScript REPL that takes its input from the Rebel readline editor.

Note that I am using the `clojure` command and not the `clj` command
because the latter wraps the process with another readline program (`rlwrap`).

Alternatively you can specify an alias in your `$HOME/.clojure/deps.edn`

```clojure
{
 ...
 :aliases {:rebel {:extra-deps {rebel-readline-cljs {:mvn/version "0.1.0-SNAPSHOT"}}}
}
```

And then run with a simpler:

```shell
$ clojure -R:rebel -m rebel-readline-cljs.main
```

#### Clone repo

`lein trampoline run` will get you into a Nashorn backed CLJS repl with the readline working.

## Usage

A simple usage example:

```clojure
(let [repl-env (cljs.repl.nashorn/repl-env)
      line-reader (rebel-readline.core/line-reader 
                   (rebel-readline-cljs.service/create {:repl-env repl-env}))]
  (cljs.repl/repl repl-env
   ;; the prompt is supplied by the readline program
   :prompt (fn [])
   :read (rebel-readline-cljs.core/cljs-repl-read line-reader)))
```

Or:

```clojure
(let [repl-env (cljs.repl.nashorn/repl-env)]
  (rebel-readline.core/with-readline-input-stream (rebel-readline-cljs.service/create 
                                                   {:repl-env repl-env})
    (cljs.repl/repl repl-env :prompt (fn [])))
```

## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
