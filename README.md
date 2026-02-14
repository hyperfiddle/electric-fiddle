# Hyperfiddle demos repo — Electric Fiddle

This is an **internal playground repo** where we host a lot of our demos, live examples, and little snippets and deploy them to prod. In its current form it's **not intended for public use**. We only leave it published so we can link to the source code of certain demos when we need to.

For a minimalist Electric starter repo, see https://gitlab.com/hyperfiddle/electric3-starter-app.

## Getting started — dev setup

```sh
npm install
#clj -X:deps prep :aliases "[:private]" :force true # for private monorepo only
clj -X:build:private compile-java # for spring framework demos
clj -A:dev:private:electric-tutorial:dustingetz -X dev/-main
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

* **local dev**: the [dev entrypoint](src-dev/dev.cljc) uses a [clever reader trick](src-dev/load_dev_fiddles!.cljc) to drive Clojure/Script namespace `:require` directives dynamically from a config file, [`electric-fiddle.edn`](src-dev/electric-fiddle.example.edn). Clone the example file to override it locally, and remember to restart your REPL.

* **prod**: the [prod entrypoint](src-prod/prod.cljc) runs only one fiddle at a time, with an isolated classpath. Prod has a mandatory [build](src-build/build.clj), which bakes the Electric application client program. At compile time, both the Electric program hash (derived from git) and the fiddle entrypoint namespace are known statically, and built into both the client assets and also the server asset `resources/electric-manifest.edn` which is used to ensure that the client & server agree about the baked Electric program hash. At runtime, the prod entrypoint uses the reader trick to lookup the name of the entrypoint to run.

This is all done in a rather tiny amount of LOC, so we feel it is reasonable to expect you to understand it.

## Deployment

```shell

clj -X:build:prod:electric-tutorial build-client :build/fiddle-ns docs-site.sitemap
clj -M:prod:electric-tutorial -m prod

# Uberjar
clj -X:build:prod:electric-tutorial uberjar :build/fiddle-ns docs-site.sitemap :build/fiddle-deps-alias :electric-tutorial :build/jar-name '"app.jar"'
java -cp target/app.jar clojure.main -m prod

# Docker
docker build --build-arg HYPERFIDDLE_FIDDLE_NS=docs-site.sitemap --build-arg HYPERFIDDLE_FIDDLE_DEPS_ALIAS=electric-tutorial --build-arg VERSION=$(git rev-parse HEAD) -t electric-fiddle:latest .
docker run --rm -it -p 8080:8080 electric-fiddle:latest

# Fly
fly deploy --remote-only --build-arg VERSION=$(git rev-parse HEAD)
```

## License
Electric v3 is **free for bootstrappers and non-commercial use,** and otherwise available commercially under a business source available license, see: [Electric v3 license change](https://tana.pub/lQwRvGRaQ7hM/electric-v3-license-change) (2024 Oct). License activation is experimentally implemented through the Electric compiler, requiring **compile-time** login for **dev builds only**. That means: no license check at runtime, no login in prod builds, CI/CD etc, no licensing code even on the classpath at runtime. This is experimental, but seems to be working great so far. We do not currently require any account approval steps, just log in. There will be a EULA at some point once we finalize the non-commercial license, for now we are focused on enterprise deals which are different.
