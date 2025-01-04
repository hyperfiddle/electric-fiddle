# Build instructions

```
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
```