# rebel-readline-nrepl

[![Clojars Project](https://img.shields.io/clojars/v/com.bhauman/rebel-readline-nrepl.svg)](https://clojars.org/com.bhauman/rebel-readline-nrepl)

`rebel-readline-nrepl` is a Clojure library that brings the flexibility of Rebel Readline to your terminal as an nREPL (Network REPL) client.

## Prerequisites

Before you begin, make sure you have the following installed:

- [Clojure CLI tools](https://clojure.org/guides/install_clojure)

## Installation

### As a Clojure Tool

1. Add `rebel-readline-nrepl` to your `./clojure/deps.edn` file under aliases:

    ```clojure
    {
      :aliases {
        :nrebel {
          :extra-deps {com.bhauman/rebel-readline-nrepl {:mvn/version "0.1.5"}}
          :exec-fn rebel-readline.nrepl/connect
          :exec-args {:background-print false} ;; Optional configuration parameters
          :main-opts ["-m" "rebel-readline.nrepl.main"]
        }
      }
    }
    ```

2. Launch it via the command line:

    ```bash
    clojure -T:nrebel :port <50668> 
    ```

### Alternative Installation Method

You can also install `rebel-readline-nrepl` as a Clojure Tool with the following command:

```bash
clojure -Ttools install-latest :lib com.github.bhauman/rebel-readline :coord '{:deps/root "rebel-readline-nrepl"}' :as nrebel
```

Call it with:

```bash
clojure -Tnrebel connect :port <50668>
```

## Usage

### Starting an nREPL Server

To get started, you need an nREPL server. You can spin up a basic nREPL server by executing:

```bash
clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.1"}}}' -M -m nrepl.cmdline --port 7888
```

This command starts an nREPL server and outputs the port it is listening on. Usually, you set up the nREPL server as part of your Clojure project. Refer to the [nREPL server documentation](https://nrepl.org/nrepl/1.3/usage/server.html) for additional instructions.

### Connecting to the nREPL Server

Once the nREPL server is running, you can connect to it using
`rebel-readline-nrepl`.If you installed `rebel-readline-nrepl` in your
`.clojure/deps.edn` as above hten you can run:

```bash
clojure -T:nrebel :port 7888
```

To specify the host (default is `localhost`):

```bash
clojure -T:nrebel :host localhost :port 7888
```

If you installed it as a Clojure Tool, connect like so:

```bash
clojure -Tnrebel connect :host localhost :port 7888
```

Alternatively, if it's in your classpath you can invoke it directly:

```bash
clojure -m rebel-readline.nrepl.main --host localhost --port 7888
```

### Integrating with Your Project

To include `rebel-readline-nrepl` in your project directly, add it to your `deps.edn` file:

```clojure
{:aliases
 {:nrebelly
  {:extra-deps {com.bhauman/rebel-readline-nrepl {:mvn/version "NOT-PUBLISHED-YET"}}
   :exec-fn rebel-readline.nrepl/connect
   :exec-args {:host "localhost" :port 7888}}}}
```

You can execute it with:

```bash
clojure -T:nrebelly
```

## TLS Support

For secure connections, refer to the [nREPL TLS documentation](https://nrepl.org/nrepl/1.3/usage/tls.html) for steps on generating keys and starting a TLS-enabled nREPL server.

Connect over TLS by specifying the TLS key file:

```bash
clojure -T:nrebel :port 50668 :tls-key-file '"client.keys"'
```

## Configuration Parameters

`rebel-readline-nrepl` supports specific configuration options:

- `:port` - Required port number for the nREPL server.
- `:host` - Optional; defaults to `localhost`.
- `:tls-key-file` - Path to the TLS key file.
- `:background-print` - Boolean indicating whether to allow background threads to continue printing.

For  configuration details, refer back to the Rebel Readline documentation [here](../README.md#config-parameters).

### License

Copyright © 2023 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
