# How to build for prod

## Uberjar (Jetty 10+)

```shell
clojure -X:prod:build uberjar :version '"'$(git rev-parse HEAD)'"' :build/jar-name '"electric-starter-app.jar"'
java -cp target/electric-starter-app.jar clojure.main -m prod
```

## Uberjar (Jetty 9)

```shell
clojure -X:jetty9:prod:build uberjar :version '"'$(git rev-parse HEAD)'"' :shadow-build :prod-jetty9 :aliases '[:jetty9 :prod]' :build/jar-name '"electric-starter-app.jar"'
java -cp target/electric-starter-app.jar clojure.main -m prod-jetty9
```

## Docker

```shell
docker build --build-arg VERSION=$(git rev-parse HEAD) -t electric-starter-app:latest .
docker run --rm -it -p 8080:8080 electric-starter-app:latest
```

## Fly

```shell
fly deploy --remote-only --build-arg VERSION=$(git rev-parse HEAD)
```
