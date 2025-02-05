# 0.1.5 Introducing nREPL support

The `rebel-readline-nrepl` package offers nREPL support.

For detailed information, please refer to the `../rebel-readline-nrepl/README.md`. This library is separate (`[com.bhauman/rebel-readline-nrepl "0.1.5"]`) to keep startup time for `rebel-readline` lean.

Support for a command-line interface (CLI) is now available through both `rebel-readline.main` and `rebel-readline.tool` entry points, along with the ability to specify a configuration file.

Other updates include:
* b11eaf8: Introduction of a :neutral-screen-theme
* 9773dab: Enhanced lightweight Ctrl-C interrupt handling
* Fixed issue #219: Removed lazy loading of packages

# 0.1.4 Removed regex stack overflow for large strings

It is very confusing to get an error from the value printer, so this merits a release.

* https://github.com/bhauman/rebel-readline/issues/161

# 0.1.3 Improved Completion and Faster startup

Rebel Readline loads 2x faster and Completion now narrows choices as you type.

* thanks to Alexander Yakushev for demonstrating how make startup faster
* thanks to Michał Buczko for greatly improving how completion works

# 0.1.2 Vi improvements and key binding configuration

* Fixed PrintWriter->on name collision warning for Clojure 1.9
* Added additional vi like bindings for clojure actions to :vi-cmd key map
* make readline history file project local
* make it easier to alter-var-root core repls to rebel-readline repls
* fix blow up when COLORFGBG isn't formatted correctly
* fix repl key-map swtiching feedback
* add a clojure-force-accept-line widget that submits a line regardless
* allow key binding configuration in the rebel_readline.edn config file

# 0.1.1 Initial release
