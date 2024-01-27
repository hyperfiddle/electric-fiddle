# Electric Datomic Browser


You need [Datomic pro](https://docs.datomic.com/pro/releases.html) (now free!) to run this demo.

## Getting started

```shell
# get Datomic and also install the mbrainz example data set
./datomic_fixtures.sh

# Run Datomic
./state/datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>state/datomic.log 2>&1 &

# Run the electric-fiddle app and REPL
clj -A:dev:datomic-browser

# From the fiddle REPL, load the datomic-browser fiddle
(dev/load-fiddle! 'datomic-browser)

# Run the fiddle server
(dev/-main)
```

http://localhost:8080
