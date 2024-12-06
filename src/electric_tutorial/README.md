# Build instructions

```
# prod local
clojure -X:build:prod:electric-tutorial build-client :build/fiddle-ns electric-tutorial.fiddles
clj -M:prod:electric-tutorial -m prod

# prod uberjar
clojure -X:build:prod:electric-tutorial uberjar :build/fiddle-ns electric-tutorial.fiddles :build/fiddle-deps-alias electric-tutorial :build/jar-name "app.jar"
java -cp app.jar clojure.main -m prod

# deploy via dockerfile
fly deploy --remote-only --config src/electric_tutorial/fly.toml
```