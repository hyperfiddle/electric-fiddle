FROM clojure:openjdk-11-tools-deps AS build
WORKDIR /app
# electric-user-version is computed from git sha during clj build
COPY .git .git
#COPY .m2 /root/.m2
COPY shadow-cljs.edn shadow-cljs.edn
COPY deps.edn deps.edn
COPY src src
COPY src-framework src-framework
COPY src-build src-build
COPY src-prod src-prod
COPY vendor vendor
COPY resources resources

ARG REBUILD=unknown
ARG HYPERFIDDLE_FIDDLE_NS
ARG HYPERFIDDLE_FIDDLE_DEPS_ALIAS
RUN clojure -A:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok         # preload
RUN clojure -A:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok   # preload
RUN clojure -X:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS uberjar \
    :hyperfiddle.fiddle-build/fiddle-ns $HYPERFIDDLE_FIDDLE_NS \
    :hyperfiddle.fiddle-build/fiddle-deps-alias $HYPERFIDDLE_FIDDLE_DEPS_ALIAS \
    :hyperfiddle.fiddle-build/jar-name app.jar

FROM clojure:openjdk-11-tools-deps AS datomic-fixtures
WORKDIR /app
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends unzip curl wget
#COPY state/datomic-pro-1.0.6735.zip state/datomic-pro-1.0.6735.zip
#COPY state/mbrainz.tar state/mbrainz.tar
COPY src/datomic_browser/datomic_fixtures.sh datomic_fixtures.sh
RUN ./datomic_fixtures.sh

FROM amazoncorretto:11 AS app
WORKDIR /app
#RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends netcat
COPY --from=build /app/app.jar app.jar
COPY --from=datomic-fixtures /app/state /app/state
COPY src/datomic_browser/prod.sh prod.sh
EXPOSE 8080
CMD ./prod.sh