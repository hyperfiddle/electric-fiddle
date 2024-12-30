# Datomic Browser — Electric v3

You need [Datomic pro](https://docs.datomic.com/pro/releases.html) (now free!) to run this demo.

https://user-images.githubusercontent.com/124158/219978031-939344eb-4489-4b97-af9f-4b2df38c70db.mp4

# Dev

```shell
./datomic_fixtures.sh # get Datomic and also install the mbrainz example data set
./state/datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>state/datomic.log 2>&1 &
# add `datomic-browser.mbrainz-browser` to electric-fiddle.edn
clj -A:dev:datomic-browser # Run the electric-fiddle app and REPL
(dev/-main) # Run the fiddle server
http://localhost:8080
```

# Prod

```
# prod local
clojure -X:build:prod:datomic-browser build-client :hyperfiddle.fiddle-build/fiddle-ns datomic-browser.mbrainz-browser
clj -M:prod:datomic-browser -m prod

# prod uberjar
clojure -X:build:prod:datomic-browser uberjar :hyperfiddle.fiddle-build/fiddle-ns datomic-browser.mbrainz-browser :hyperfiddle.fiddle-build/fiddle-deps-alias datomic-browser :hyperfiddle.fiddle-build/jar-name "app.jar"
java -cp target/app.jar clojure.main -m prod
```