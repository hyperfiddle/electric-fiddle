# Electric v3 Starter App

## Links

* Electric github with source code: https://github.com/hyperfiddle/electric
* Tutorial: https://electric.hyperfiddle.net/

## Getting started

* Shell: `clj -A:dev -X dev/-main`. 
* Login instructions will be printed
* REPL: `:dev` deps alias, `(dev/-main)` at the REPL to start dev build
* App will start on http://localhost:8080
* Electric root function: [src/electric_starter_app/main.cljc](src/electric_starter_app/main.cljc)
* Hot code reloading works: edit -> save -> see app reload in browser

```shell
# Prod build
clj -X:build:prod build-client
clj -M:prod -m prod

# Uberjar (optional)
clj -X:build:prod uberjar :build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# Docker
docker build --build-arg VERSION=$(git rev-parse HEAD) -t electric3-starter-app:latest .
docker run --rm -it -p 8080:8080 electric3-starter-app:latest


# Fly
fly deploy --remote-only --build-arg VERSION=$(git rev-parse HEAD)
```

## License
Electric v3 is **free for bootstrappers and non-commercial use,** and otherwise available commercially under a business source available license, see: [Electric v3 license change](https://tana.pub/lQwRvGRaQ7hM/electric-v3-license-change) (2024 Oct). License activation is experimentally implemented through the Electric compiler, requiring **compile-time** login for **dev builds only**. That means: no license check at runtime, no login in prod builds, CI/CD etc, no licensing code even on the classpath at runtime. This is experimental, but seems to be working great so far. We do not currently require any account approval steps, just log in. There will be a EULA at some point once we finalize the non-commercial license, for now we are focused on enterprise deals which are different.
