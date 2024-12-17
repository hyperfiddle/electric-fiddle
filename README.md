# Electric Fiddle

We publish all our demos here in one place. This is how we fiddle around with our many demos at work.

This repo is structured to allow multiple "fiddles" (little apps) to share common dev infrastructure (e.g. routing, databases). During dev, these classpaths are merged, but prod classpaths are isolated so that each fiddle can deployed individually.

* **local dev**: the [dev entrypoint](src-dev/dev.cljc#L10) uses a [clever reader trick](src-dev/load_dev_fiddles!.cljc#L6) to drive Clojure/Script namespace `:require` directives dynamically from a config file, [`electric-fiddle.edn`](src-dev/electric-fiddle.edn).

* **prod**: the [prod entrypoint](src-prod/prod.cljc) runs only one fiddle at a time, with an isolated classpath. Prod has a mandatory [build](src-build/hyperfiddle/fiddle_build.clj), which bakes the Electric application client program. At compile time, both the Electric program hash (derived from git) and the fiddle entrypoint namespace are known statically, and built into both the client assets and also the server asset `resources/electric-manifest.edn` which is used to ensure that the client & server agree about the baked Electric program hash. At runtime, the prod entrypoint uses the reader trick to lookup the name of the entrypoint to run.

This is all done in a rather tiny amount of LOC, so we feel it is reasonable to expect you to understand it.

For a minimalist starter example, see https://github.com/hyperfiddle/electric3-starter-app.

## Quick Start

Begin with an example "Hello World" fiddle (`src/hello_fiddle/hello-fiddle.cljc`):

```shell
$ clj -A:dev
INFO  dev: {:host "0.0.0.0", :port 8080, :resources-path "public", :manifest-path "public/js/manifest.edn"}
INFO  dev: Starting Electric compiler and server...
shadow-cljs - nREPL server started on port 9001
[:dev] Configuring build.
[:dev] Compiling ...
[:dev] Build completed. (231 files, 2 compiled, 0 warnings, 2.46s)
INFO  electric-fiddle.server-jetty: 👉 http://0.0.0.0:8080
```

## Load more fiddles

In [`electric-fiddle.edn`](electric-fiddle.edn), under `:hyperfiddle/fiddles`, add `electric-tutorial.tutorial`:

```diff
 {:loaded-fiddles [hello-fiddle.hello-fiddle
+                  electric-tutorial.tutorial ; requires :electric-tutorial deps alias and `npm install`
                   ]
 }
```

Restart your REPL with the required dependencies:
```shell
npm install
clj -A:dev:electric-tutorial
...
INFO  electric-fiddle.server-jetty: 👉 http://0.0.0.0:8080
```
The fiddle index page now shows a new entry for `electric-tutorial`.

# Prod build (deploy one fiddle at a time)

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
fly deploy --remote-only --config src/electric_tutorial/fly.toml --dockerfile src/electric_tutorial/Dockerfile
fly status
fly platform vm-sizes
fly scale show
fly scale count 1 --region ewr
fly scale count 1 --region cdg
fly scale count 1 --region sjc
fly scale count 1 --region gru
```
