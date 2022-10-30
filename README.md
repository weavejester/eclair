# Eclair

Eclair is an **e**xtensible **c**onfiguration **l**anguage for Clojure.
It's a superset of [edn][] and fulfils a similar role to [Aero][], but
is not limited to using data readers to provide extra functionality.

**Note:** This is a new library, and there are likely bugs.

[edn]: https://github.com/edn-format/edn
[aero]: https://github.com/juxt/aero

## Example

```
;; Eclair supports unquoting variables and string interpolation
:example/server
{:port ~port
 :host ~(or host "localhost")
 :url  "http://~{host}:~{port}"}

:example/strings
{:pattern   #re #"\d+" ;; raw strings and regular expressions
 :long-text """
Strings can also be "triple quoted" to allow for inner quotes without
the need to escape them.
"""}

;; Additional data can be spliced in from other sources
~@(include "more.ecl")
```

## Installation

Add the following dependency to your deps.edn file:

    dev.weavejester/eclair {:mvn/version "0.1.0"}

Or to your Leiningen project file:

    [dev.weavejester/eclair "0.1.0"]

## Usage

To read from a string, use the `eclair.reader/read-string` function:

```clojure
(require '[eclair.reader :as eclair])

(eclair/read-string "{:x 1}")  ;; => {:x 1}
```

To read from a resource on the classpath, use the `eclair.io/load`
function:

```clojure
(require '[eclair.io :as eio])

(eio/load "example.ecl")
```

The `read-string` function is pure by default. The `load` function will
return the same result given the same arguments as long as the resources
on the classpath do not change.

Both functions may take an option map. There are three supported
options:

* `:vars`      - a map of variables
* `:resolvers` - a map of resolver functions
* `:readers`   - a map of reader functions

The `:readers` option operates the same as the corresponding key in the
`clojure.edn/read-string` function. It's a map of symbols to functions
that determine how reader tags are interpreted.

The `:vars` and `:resolvers` options are covered in more depth in the
following section, under Variables and Resolvers respectively.

## Syntax

Eclair is a superset of edn, so any valid edn syntax is supported by
Eclair. The following additional syntax may also be used:

### Variables

Eclair borrows the unquote operator (`~`) from Clojure to introduce
external variables into the configuration. For example:

```
;; example1.ecl
{:port ~port}
```

Variables are supplied with the `:vars` option to either `read-string`
or `load`:

```clojure
(eio/load "example1.ecl" {:vars {'port 8080}})a
```

This resolves to the following data structure:

```clojure
{:port 8080}
```

The unquote splicing option (`~@`) may also be used, and unlike in
Clojure, can also be used in a map without restriction:

```
;; example2.ecl
{:port 8080, ~@extra-options}
```

The `extra-options` variable may be a map. In Eclair, maps are expanded
out in the same as vectors and lists, rather than a collection of
key/value pairs.

```clojure
(eio/load "example2.ecl" {:vars {'extra-options {:host "localhost"}}})
```

This resolves to the following data structure:

```clojure
{:port 8080, :host "localhost"}
```

The keys in the `:vars` map may be symbols, keywords or strings. This
allows maps from environment or parsed argument lists to be easily
passed into an Eclair configuration.

When a variable is missing from the `:vars` map, it resolves to `nil`.

### Resolvers

A resolver is loosely equivalent to a Clojure function. They allow basic
control structures to be embedded in the configuration.

Like variables, they use the unquote (`~`) and unquote splice operators
('~@'):

```
;; example3.ecl
{:port ~(increment 8080)}
```

Resolvers are defined like readers, as a map of symbols to functions:

```clojure
(eio/load "example3.ecl" {:resolvers {'increment #(+ % 1)}})
```

This resolves to the following data:

```clojure
{:port 8081}
```

There are three inbuilt resolvers:

* `?`   - operates like `cond`
* `or`  - operates like `or`
* `ref` - retrieve a value from another part of the configuration

The `load` function adds one more:

* `include` - loads a classpath resource given a relative path

The `?` resolver has the same properties as `cond`, and is generally
used for changing configuration values based on profiles or boolean
options:

```
{:port ~(? dev 8080, prod 80)}
```

Note that within an unquote, nested variables and resolvers don't need
to be unquoted again. If you want to use a symbol, use the syntax quote
operator:

```
{:function ~(? dev `mock, prod `real)}
```

The `or` resolver will return the first non-falsey value, and is often
used to supply default values:

```
{:port ~(or port 8080)}
```

The `ref` resolver can be used to reference other values in the
configuration:

```
{:server {:port 8080}
 :client {:port ~(ref :server :port)}}
```

Finally, the `include` resolver is available when the `load` function is
used. This allows a configuration to be split into multiple files. Paths
are relative, but can be made absolute with an initial `/`.

```
{:server ~(include "server.ecl")
 :client ~(include "../client.ecl")
 ~@(include "/dev/user.ecl")
 ~@(include "/dev/test.ecl")}
```

The unquote splice operator is useful when including a file inline.

### Strings

Eclair supports string interpolation. Variables and resolvers can be
embedded in strings using the `~{}` syntax:

```
{:url "http://~{host}:~{(or port 8080)}"}
```

Eclair also supports triple-quoted strings, so that single quotes can be
used with out needing to be escaped:

```
{:text """This is an "example"."""}
```

Finally, Eclair supports raw strings, which it denotes with a prepended
`#`:

```
{:text #"This is a \r\a\w string. There is no ~{interpolation}"}
```

Here Eclair differs from Clojure, which uses this syntax to denote a
regular expression. In Eclair, a raw string has no interpolation, and
the only recognized escape characters are `\"` and `\\`.

Regular expressions are denoted with the `#re` tag. Thus in Eclair a
regular expression would be written:

```
{:pattern #re #"\d+"}
```

### Miscellaneous

Like Clojure, Eclair supports metadata:

```
^:example {:foo 1}

^{:example true} {:foo 1}
```

Similarly, Eclair supports the Clojure syntax for qualified maps:

```
#:example {:foo 1}
```

Note that because Eclair has no concept of a current namespace, the `::`
syntax sugar does not exist in Eclair.

A top-level map may have its brackets omitted. Thus:

```
:foo 1
:bar 2
```

Is equivalent to:

```
{:foo 1
 :bar 2}
```

## License

Copyright © 2022 James Reeves

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
