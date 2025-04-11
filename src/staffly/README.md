# Dev

* in-mem datomic database is automatic

# Prod

```
# prod local
clojure -X:build:prod:staffly build-client :hyperfiddle.fiddle-build/fiddle-ns staffly.staffly
clj -M:prod:staffly -m prod

# prod uberjar
clojure -X:build:prod:staffly uberjar :hyperfiddle.fiddle-build/fiddle-ns staffly.staffly :hyperfiddle.fiddle-build/fiddle-deps-alias staffly :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# deploy via dockerfile
fly deploy --remote-only --config src/staffly/fly.toml
```