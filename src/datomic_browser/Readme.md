# Electric Datomic Browser


You need [Datomic pro](https://docs.datomic.com/pro/releases.html) (now free!) to run this demo.

## Getting started
To get Datomic and install the sample data set:
```shell
./datomic_fixtures.sh
```

Run Datomic:
```shell
./state/datomic-pro/bin/transactor config/samples/dev-transactor-template.properties >>state/datomic.log 2>&1 &
```

Run Electric Fiddle:
```shell
clj -A:dev:datomic-browser
```
At the repl:
```clojure
(dev/-main)
(dev/load-fiddle! 'datomic-browser)
```

Open your browser at [http://localhost:8080](http://localhost:8080).



