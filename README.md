# metaref/async

`metaref.async` is a Clojure library that provides a similiar API to `clojure.core.async` but with a different implementation that 
uses Java's VirtualThread (Project Loom). The Channel and Select/Alt implementations are in Java and the Clojure API is a thin wrapper around them.

## Installation

To use `metaref.async` in your Clojure project, add the following dependency to your `deps.edn`:

```clojure
io.github.metaref/async {:git/tag "v0.0.1" :git/sha "1eea072"}}
```

Before using the library, you must first prepare it with the following command:

```bash
clojure -X:deps prep
```

** Make sure you have at least Java 17 installed ** 

## Examples

There are some examples in the `test` directory based on some talks of the Go language concurrency model. Just add the `test` alias to your classpath

## Usage

Run the project's tests:

    $ clojure -T:build test

Run the project's CI pipeline and build a JAR:

    $ clojure -T:build ci

This will produce an updated `pom.xml` file with synchronized dependencies inside the `META-INF`
directory inside `target/classes` and the JAR in `target`. You can update the version (and SCM tag)
information in generated `pom.xml` by updating `build.clj`.

Install it locally (requires the `ci` task be run first):

    $ clojure -T:build install

## License

Copyright © 2024 Gabriel Luque

Distributed under the Eclipse Public License version 1.0.
