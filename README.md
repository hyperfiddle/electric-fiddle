# Electric Fiddle

We publish all our demos here in one place. This is how we fiddle around with our many demos at work.

This repo is structured to allow multiple "fiddles" (little apps) to run simultaneously on the same merged local dev classpath, with some common dev infrastructure (e.g. routing and example databases). Prod classpaths are isolated, so that each fiddle can be deployed individually and separately.

* **local dev**: the [dev entrypoint](src-dev/dev.cljc) uses a [clever reader trick](src-dev/fiddles.cljc) to drive Clojure/Script namespace `:require` directives from a config file, [`electric-fiddle.edn`](electric-fiddle.edn).

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
# "Hello World" prod build

clojure -X:build:prod build-client :hyperfiddle/domain hello-fiddle
clojure -X:build:prod build-client :hyperfiddle/domain hello-fiddle :debug false :verbose false :optimize true
clj -M:prod -m prod

# With extra dependencies

npm install
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle/domain electric-tutorial
clj -M:prod:electric-tutorial -m prod
# http://localhost:8080/electric-tutorial.tutorial!%54utorial/electric-tutorial.demo-two-clocks!%54wo%43locks

# Uberjar

clojure -X:build:prod uberjar :hyperfiddle/domain hello-fiddle :build/jar-name "app.jar"
java -cp app.jar clojure.main -m prod

# Fly.io deployment

fly deploy --remote-only --config src/hello_fiddle/fly.toml
```
