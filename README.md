# Electric v3 Starter App

## Instructions

Dev build:

* Shell: `clj -A:dev -X dev/-main`, or repl: `(dev/-main)`
    * authentication instructions will be printed
* App will start on http://localhost:8080
* Electric root function: [src/electric_starter_app/main.cljc](src/electric_starter_app/main.cljc)
* Hot code reloading works: edit -> save -> see app reload in browser

Prod build:

```shell
clj -X:build:prod build-client
clj -M:prod -m prod
```

Uberjar (optional):
```shell
clj -X:build:prod uberjar :build/jar-name "target/app.jar"
java -cp target/app.jar clojure.main -m prod
```

Deployment:
- [Dockerfile](Dockerfile)
- fly.io deployment [fly.toml](fly.toml)
    - through github actions: [.github/workflows/deploy.yml](.github/workflows/deploy.yml)
