# Hyperfiddle demos monorepo — Electric Fiddle

* tutorial app
* explorer and datomic browser demos
* dustingetz namespace
* deploy infrastructure to deploy one namespace at a time with isolated classpath

For a minimalist starter example, see https://gitlab.com/hyperfiddle/electric3-starter-app.

## Quick start

```sh
npm install
clj -A:dev:electric-tutorial:dustingetz -X dev/-main
...
[:dev] Compiling ...
Please sign up or login to activate:  https://hyperfiddle-auth.fly.dev/login?redirect-uri=http%3A%2F%2Flocalhost%3A8081
...
INFO  electric-fiddle.server-jetty: 👉 http://0.0.0.0:8080
```
* note: two deps aliases, you must include them both
* note: Electric login is required for dev builds

## "Fiddle" classpath infrastructure (optional)

This repo is structured to allow multiple "fiddles" (little apps) to share common dev infrastructure (e.g. routing, databases). During dev, these classpaths are merged, but prod classpaths are isolated so that each fiddle can deployed individually.

* **local dev**: the [dev entrypoint](src-dev/dev.cljc#L10) uses a [clever reader trick](src-dev/load_dev_fiddles!.cljc#L6) to drive Clojure/Script namespace `:require` directives dynamically from a config file, [`electric-fiddle.edn`](src-dev/electric-fiddle.example.edn). Clone the example file to override it locally, and remember to restart your REPL.

* **prod**: the [prod entrypoint](src-prod/prod.cljc) runs only one fiddle at a time, with an isolated classpath. Prod has a mandatory [build](src-build/hyperfiddle/fiddle_build.clj), which bakes the Electric application client program. At compile time, both the Electric program hash (derived from git) and the fiddle entrypoint namespace are known statically, and built into both the client assets and also the server asset `resources/electric-manifest.edn` which is used to ensure that the client & server agree about the baked Electric program hash. At runtime, the prod entrypoint uses the reader trick to lookup the name of the entrypoint to run.

This is all done in a rather tiny amount of LOC, so we feel it is reasonable to expect you to understand it.

## Prod build (common infra to deploy one fiddle at a time)

```shell
# fiddle prod build
npm install
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle.fiddle-build/fiddle-ns docs-site.sitemap :debug false :verbose false :optimize true
clj -M:prod:electric-tutorial -m prod

# fiddle uberjar
clojure -X:build:prod:electric-tutorial uberjar :hyperfiddle.fiddle-build/fiddle-ns docs-site.sitemap :hyperfiddle.fiddle-build/fiddle-deps-alias electric-tutorial :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# fiddle docker
docker build -t electric-tutorial:latest . --build-arg HYPERFIDDLE_FIDDLE_NS=docs-site.sitemap --build-arg HYPERFIDDLE_FIDDLE_DEPS_ALIAS=electric-tutorial --build-arg ELECTRIC_USER_VERSION=$(git rev-parse HEAD)
docker run --rm -it -p 8080:8080 electric-tutorial:latest

```
