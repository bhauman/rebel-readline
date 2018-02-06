# rebel-readline

A terminal readline library for Clojure Dialects

WORK IN PRooooooGRESS!!

## Important note!!! 

The rebel line reader will attempt to manipulate the terminal
that initiated the JVM process. For this reason it is important
to start your JVM in a terminal.

That means launching your java process using the

 * the java command
 * the Clojure clj tool
 * lein trampoline 
 * boot - would need to run in boot's worker pod

Launching from a process initiated by lein will not work and
launching from a boot pod will not cut it either.

## Quick start

`lein trampoline run` will get you into a clojure repl with the readline working.

## Quick Lay of the land

You should look at `rebel-readline.main` and `rebel-readline.core`
to give you top level usage information.

The meat of the functionality is in `rebel-readline.line-reader` and
`rebel-readline.widgets.base` everything else is just support.

## Keybindings

**Bindings of interest**

* Ctrl-X_Ctrl-D => Show documentation for word at point
* Ctrl-X_Ctrl-S => Show source for word at point
* Ctrl-X_Ctrl-A => Show apropos for word at point
* Ctrl-X_Ctrl-E => Inline eval for SEXP before the point

The built-in keybindings that are currently in use can be seen in the
Jline source code
[here](https://github.com/jline/jline3/blob/52d2c894ac8966a84313018302afa1521ea6fec4/reader/src/main/java/org/jline/reader/impl/LineReaderImpl.java#L5075-L5154)

** I have not yet verified how all of the built-in jline commands behave against Clojure code **

I will porbably initially remove poorly behaving commands and focus on
adding Clojure specific commands back as the get properly
implemented. However, paredit functionality is going to take
precendence to reimplementing the built-in commands above.


## License

Copyright © 2018 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
