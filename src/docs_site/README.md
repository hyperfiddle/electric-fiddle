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
--build-arg VERSION=$(git rev-parse HEAD) \
-f electric-fiddle/src/docs_site/Dockerfile \
-t electric-tutorial:latest .

docker run --rm -it -p 8080:8080 electric-tutorial:latest

# fly deploy, from monorepo root
fly deploy --remote-only --config electric-fiddle/src/docs_site/fly.toml --dockerfile electric-fiddle/src/docs_site/Dockerfile --build-arg VERSION=$(git rev-parse HEAD)
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

# Diagnose issues on a specific machine

Fly.io route requests to the closest geographical region, and through load
balancers. Sometimes a specific machine is misbehaving and it's not obvious how
to detect or access it.

From current folder run `fly machines list`.
Then you have 3 options:
- `fly ssh console --machine <machine-id>`
  - `jps -l`
  - `jstack <pid>`
- Connect through a public VPN into the target region. You'll still go through
  the Fly's load balancer, so while this is convenient, it only work reliably
  for deployments with up to one machine per region.
- Connect directly to the machine by ip, through a wireguard tunnel provided by fly.io:
  1. `$> fly wireguard create` – store output in some .conf file
  2. Install wireguard (e.g. from mac app store)
  3. Import .conf file generated above into your wireguard client
  4. Ensure the imported config (tunnel) is enabled
  5. `$> fly machines list` – note the IPv6 column
  6. Browse to `http://[<ipv6>]:8080` – url syntax for IPv6 uses square brackets
  ```