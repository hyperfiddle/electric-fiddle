# Build instructions

```
# prod local
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle/domain electric-tutorial
clj -M:prod:electric-tutorial -m prod

# prod uberjar
clojure -X:build:prod:electric-tutorial uberjar :hyperfiddle/domain electric-tutorial :build/jar-name "app.jar"
java -cp app.jar clojure.main -m prod

# deploy via dockerfile
fly deploy --remote-only --config src/electric_tutorial/fly.toml
```