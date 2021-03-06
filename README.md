# coattail

Clojure/Script OpenAPI tooling to afford instant leverage.

**EARLY-ALPHA: Very early days. Breaking all the time. Not for production use.**


## Rationale

OpenAPI driven

- Payload Parsing
- Payload Writing
- Server implementation
- Client implementation
- Web routing (with Ring support)


## Usage

[![Clojars Project](https://img.shields.io/clojars/v/coattail.svg)](https://clojars.org/coattail)

### Supported stack

- Java 8 or higher
- Clojure 1.9 or higher
- ClojureScript 1.10 or higher


## Development

### Setting up

Install Java 8 or higher, Node.js 8 or higher.

Once you have cloned this repo, `cd` into it and clone the following repo:

```shell
cd external
curl https://petstore3.swagger.io/api/v3/openapi.json -o petstore3-openapi.json
git clone git@github.com:OAI/OpenAPI-Specification.git
cd OpenAPI-Specification
git checkout 3.1.0-rc1
```

### Running tests

```shell
TZ=UTC lein do clean, test, cljs-test
```


## License

Copyright © 2020-2021 Shantanu Kumar

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
