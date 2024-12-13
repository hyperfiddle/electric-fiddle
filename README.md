# Electric Fiddle

We publish all our demos here in one place. This is how we fiddle around with our many demos at work.

This repo is structured to allow multiple "fiddles" (little apps) to run simultaneously on the same merged local dev classpath, with some common dev infrastructure (e.g. routing and example databases). Prod classpaths are isolated, so that each fiddle can be deployed individually and separately.

* **local dev**: the [dev entrypoint](src-dev/dev.cljc) uses a [clever reader trick](src-dev/load_dev_fiddles!.cljc) to drive Clojure/Script namespace `:require` directives from a config file, [`electric-fiddle.edn`](electric-fiddle.edn).

* **prod**: the [prod entrypoint](src-prod/prod.cljc) runs only one fiddle at a time, with an isolated classpath. Prod has a mandatory [build](src-build/build.clj), which bakes the Electric application client program. At compile time, both the electric user application version number (derived from git) and the fiddle entrypoint namespace are known statically, and built into both the client assets and also the server asset `resources/electric-manifest.edn`. At runtime, the prod entrypoint uses the reader trick to lookup the name of the entrypoint to run.

This is all done in a rather tiny amount of LOC, so we feel it is reasonable to expect you to understand it.

For a minimalist starter example, see https://github.com/hyperfiddle/electric-starter-app which is able to hardcode the electric user application entrypoint functions and thereby eliminate some of this dynamism.

## Quick Start

Begin with an example "Hello World" fiddle:

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

1. Navigate to [http://localhost:8080](http://localhost:8080)
2. Corresponding source code is in `src/hello_fiddle`

## Load more fiddles

In [`electric-fiddle.edn`](electric-fiddle.edn), under `:loaded-fiddles`, add `electric-tutorial`:

```diff
 {:loaded-fiddles [hello-fiddle
+                  electric-tutorial ; requires :electric-tutorial alias and `npm install`
                   ]
 }
```

Restart your REPL with the required dependencies:
```shell
npm install
clj -A:dev:electric-tutorial
```

Navigate to [http://localhost:8080](http://localhost:8080) (or refresh your browser tab). The pages shows a new entry for `electric-tutorial`.

## Roll your own

- `mkdir src/my_fiddle`
- Add the following to `src/my_fiddle/fiddles.cljc`:
```clojure
(ns my-fiddle.fiddles
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))

(e/defn MyFiddle []
  (e/client
    (dom/h1 (dom/text "Hello from my fiddle."))))

(e/def fiddles ; Entries for the dev index
  {`MyFiddle MyFiddle})

(e/defn FiddleMain [ring-req] ; prod entrypoint
  (e/server
    (binding [e/http-request ring-req])
      (e/client
        (binding [dom/node js/document.body]
          (MyFiddle.)))))
```

- Add `my-fiddle` to `electric-fiddle.edn` > `:loaded-fiddles`.
- add your dependencies to deps.edn (under an alias) and package.json
- Restart your REPL with your new deps alias

# Prod build

Deploys one fiddle at a time.

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
fly deploy --remote-only --config src/electric_tutorial/fly.toml
```
