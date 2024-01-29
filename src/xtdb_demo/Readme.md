# Electric XTDB demo

* Adapted from [xtdb-in-a-box](https://github.com/xtdb/xtdb-in-a-box)
* Note: `XTDB_ENABLE_BYTEUTILS_SHA1=true` env var is required!


Start a REPL:
```shell
XTDB_ENABLE_BYTEUTILS_SHA1=true clj -A:dev:xtdb-demo
```
At the REPL:
```clojure
(dev/-main)
(dev/load-fiddle! 'xtdb-demo)
```

Visit [http://localhost:8080](http://localhost:8080)

Build and run for prod:

```shell
clojure -X:build:prod:xtdb-demo build-client :hyperfiddle/domain xtdb-demo
XTDB_ENABLE_BYTEUTILS_SHA1=true clj -M:prod:xtdb-demo -m prod
```
