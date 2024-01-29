This demo requires an environment variable to be set.

Run a REPL:
```shell
XTDB_ENABLE_BYTEUTILS_SHA1=true clj -A:dev:xtdb-starter
```
At the REPL:
```clojure
(dev/-main)
(dev/load-fiddle! 'xtdb-starter)
```

Build and run for prod:

```shell
clojure -X:build:prod:xtdb-starter build-client :hyperfiddle/domain xtdb-starter
XTDB_ENABLE_BYTEUTILS_SHA1=true clj -M:prod:xtdb-starter -m prod
```
