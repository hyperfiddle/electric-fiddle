# Minimal setup to run datomic browser from a jar

1. build the datomic browser jar (not uberjar!)
   - cd ../publish/electric-datomic-browser/
   - clj -X:build:prod jar

2. run with jar added to classpath
   - Note version of `../publish/electric-datomic-browser/target/datomic-browser-<git-sha>.jar`
   - put it in `./deps.edn`, like: `com.hyperfiddle/datomic-browser {:local/root "../publish/electric-datomic-browser/target/datomic-browser-<git-sha>.jar"}`
   - `clj -M:prod -m prod datomic-uri datomic:dev://localhost:4334/mbrainz-1968-1973`

## License
Electric v3 is **free for bootstrappers and non-commercial use,** and otherwise available commercially under a business source available license, see: [Electric v3 license change](https://tana.pub/lQwRvGRaQ7hM/electric-v3-license-change) (2024 Oct). License activation is experimentally implemented through the Electric compiler, requiring **compile-time** login for **dev builds only**. That means: no license check at runtime, no login in prod builds, CI/CD etc, no licensing code even on the classpath at runtime. This is experimental, but seems to be working great so far. We do not currently require any account approval steps, just log in. There will be a EULA at some point once we finalize the non-commercial license, for now we are focused on enterprise deals which are different.
