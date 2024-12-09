# Build instructions

```
# prod local
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle.fiddle-build/fiddle-ns electric-tutorial.tutorial
clj -M:prod:electric-tutorial -m prod

# prod uberjar
clojure -X:build:prod:electric-tutorial uberjar :hyperfiddle.fiddle-build/fiddle-ns electric-tutorial.tutorial :hyperfiddle.fiddle-build/fiddle-deps-alias electric-tutorial :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# deploy via dockerfile
fly deploy --remote-only --config src/electric_tutorial/fly.toml
```