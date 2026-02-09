# How to build for prod

## Uberjar (Jetty 10+)

```shell
clojure -X:build:prod:electric-tutorial uberjar :build/fiddle-ns docs-site.sitemap :build/fiddle-deps-alias :electric-tutorial :version '"'$(git rev-parse HEAD)'"'
java -cp target/electric-fiddle-*.jar clojure.main -m prod
```

## Uberjar (Jetty 9)

```shell
clojure -X:jetty9:build:prod:electric-tutorial uberjar :build/fiddle-ns docs-site.sitemap :build/fiddle-deps-alias :electric-tutorial :shadow-build :prod-jetty9 :aliases '[:jetty9 :prod :electric-tutorial]' :version '"'$(git rev-parse HEAD)'"'
java -cp target/electric-fiddle-*.jar clojure.main -m prod-jetty9
```

## Docker

```shell
docker build --build-arg HYPERFIDDLE_FIDDLE_NS=docs-site.sitemap --build-arg HYPERFIDDLE_FIDDLE_DEPS_ALIAS=electric-tutorial --build-arg VERSION=$(git rev-parse HEAD) -t electric-fiddle:latest .
docker run --rm -it -p 8080:8080 electric-fiddle:latest
```

## Fly

```shell
fly deploy --remote-only --build-arg VERSION=$(git rev-parse HEAD)
```