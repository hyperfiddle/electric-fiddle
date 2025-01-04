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

* **local dev**: the [dev entrypoint](src-dev/dev.cljc#L10) uses a [clever reader trick](src-dev/load_dev_fiddles!.cljc#L6) to drive Clojure/Script namespace `:require` directives dynamically from a config file, [`electric-fiddle.edn`](src-dev/electric-fiddle.edn). If you modify this file, remember to restart your REPL (including any clojure or npm dependencies).

* **prod**: the [prod entrypoint](src-prod/prod.cljc) runs only one fiddle at a time, with an isolated classpath. Prod has a mandatory [build](src-build/hyperfiddle/fiddle_build.clj), which bakes the Electric application client program. At compile time, both the Electric program hash (derived from git) and the fiddle entrypoint namespace are known statically, and built into both the client assets and also the server asset `resources/electric-manifest.edn` which is used to ensure that the client & server agree about the baked Electric program hash. At runtime, the prod entrypoint uses the reader trick to lookup the name of the entrypoint to run.

This is all done in a rather tiny amount of LOC, so we feel it is reasonable to expect you to understand it.

## Prod build (deploy one fiddle at a time)

```shell
# prod build
npm install
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle.fiddle-build/fiddle-ns electric-tutorial.tutorial :debug false :verbose false :optimize true
clj -M:prod:electric-tutorial -m prod

# uberjar
clojure -X:build:prod:electric-tutorial uberjar :hyperfiddle.fiddle-build/fiddle-ns electric-tutorial.tutorial :hyperfiddle.fiddle-build/fiddle-deps-alias electric-tutorial :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# docker
HYPERFIDDLE_FIDDLE_NS=electric-tutorial.tutorial
HYPERFIDDLE_FIDDLE_DEPS_ALIAS=electric-tutorial
docker build -t electric-tutorial:latest .
docker run --rm -it -p 8080:8080 electric-tutorial:latest

# fly deploy via Dockerfile
fly deploy --remote-only --config src/docs_site/fly.toml --dockerfile src/docs_site/Dockerfile
fly status
fly platform vm-sizes
fly scale show
fly scale count 1 --region ewr
fly scale count 1 --region cdg
fly scale count 1 --region sjc
fly scale count 1 --region gru
```
