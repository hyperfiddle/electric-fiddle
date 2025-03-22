# Build instructions

```
# prod build, from electric-fiddle
npm install
clojure -X:build:prod:electric-tutorial build-client :hyperfiddle.fiddle-build/fiddle-ns docs-site.sitemap :debug false :verbose false :optimize true
clj -M:prod:electric-tutorial -m prod

# uberjar, from electric-fiddle
clojure -X:build:prod:electric-tutorial uberjar :hyperfiddle.fiddle-build/fiddle-ns docs-site.sitemap :hyperfiddle.fiddle-build/fiddle-deps-alias electric-tutorial :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod

# docker, from monorepo root
docker build \
--build-arg HYPERFIDDLE_FIDDLE_NS=docs-site.sitemap \
--build-arg HYPERFIDDLE_FIDDLE_DEPS_ALIAS=electric-tutorial \
-f electric-fiddle/src/docs_site/Dockerfile \
-t electric-tutorial:latest .

docker run --rm -it -p 8080:8080 electric-tutorial:latest

# fly deploy, from monorepo root
fly deploy --remote-only --config electric-fiddle/src/docs_site/fly.toml --dockerfile electric-fiddle/src/docs_site/Dockerfile
fly status
fly scale show
fly scale count 1 --region ewr
fly scale count 1 --region cdg
fly scale count 1 --region sjc
fly scale count 1 --region gru
fly scale count 1 --region hkg
fly platform vm-sizes
fly machine restart --skip-health-checks=false
```