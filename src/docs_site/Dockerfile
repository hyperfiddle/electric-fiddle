# run from from monorepo root so that build context contains:
# - .git : for object browser demo
# - datomic-browser: for datomic mbrainz setup
# e.g. docker build -f electric-fiddle/src/docs_site/Dockerfile --build-arg VERSION=$(git rev-parse HEAD) ... 

FROM clojure:temurin-11-tools-deps-1.12.0.1501 AS datomic-fixtures
WORKDIR /app
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends unzip curl wget
# cleanup APT cache
RUN rm -rf /var/lib/apt/lists/*
COPY datomic-browser/datomic_fixtures_mbrainz_small.sh datomic_fixtures_mbrainz_small.sh
COPY datomic-browser/datomic_fixtures_mbrainz_full.sh datomic_fixtures_mbrainz_full.sh
RUN ./datomic_fixtures_mbrainz_small.sh
# RUN ./datomic_fixtures_mbrainz_full.sh
# Shaves 3Gb+ of docker image
RUN rm state/*.tar
RUN rm state/*.zip

FROM node:14.7-stretch AS node-deps
WORKDIR /app
COPY electric-fiddle/package.json package.json

RUN PUPPETEER_SKIP_DOWNLOAD=true npm install

FROM clojure:temurin-11-tools-deps-1.12.0.1501 AS build
WORKDIR /app
COPY --from=node-deps /app/node_modules /app/node_modules
# install python for libpython-clj navigator demo
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-dev python3-venv
# cleanup APT cache
RUN rm -rf /var/lib/apt/lists/*

# Rebuild if deps or commit version changes
COPY electric-fiddle/deps.edn deps.edn
ARG VERSION
ENV VERSION=$VERSION

COPY electric-fiddle/shadow-cljs.edn shadow-cljs.edn
COPY electric-fiddle/src src
COPY electric-fiddle/src-prod src-prod
COPY electric-fiddle/src-build src-build
COPY electric-fiddle/resources resources

ARG HYPERFIDDLE_FIDDLE_NS
ARG HYPERFIDDLE_FIDDLE_DEPS_ALIAS
RUN clojure -A:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok         # preload
RUN clojure -A:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok   # preload
RUN clojure -X:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS uberjar \
    :hyperfiddle.fiddle-build/fiddle-ns $HYPERFIDDLE_FIDDLE_NS \
    :hyperfiddle.fiddle-build/fiddle-deps-alias $HYPERFIDDLE_FIDDLE_DEPS_ALIAS \
    :hyperfiddle.fiddle-build/version "\"$VERSION\"" \
    :hyperfiddle.fiddle-build/jar-name app.jar

FROM amazoncorretto:11 AS app
WORKDIR /app

# install python for libpython-clj navigator demo
RUN yum update -y && \
    yum install -y \
    python3 \
    yum clean all && \
    rm -rf /var/cache/yum

# add tutorial resources (md, css, dir trees used in demos)
COPY --from=datomic-fixtures /app/state state
COPY --from=build /app/src src
# for navigator deps demo
COPY --from=build /app/deps.edn deps.edn

# for jgit demo
COPY .git git
RUN mv git .git # bypass security
RUN echo -e "/state/" > .gitignore 


# Add app
COPY --from=build /app/target/app.jar app.jar
COPY datomic-browser/run_datomic.sh run_datomic.sh

EXPOSE 8080
CMD ./run_datomic.sh && java -Dhyperfiddle.datomic.uri="datomic:dev://localhost:4334/mbrainz-1968-1973" -cp app.jar clojure.main -m prod
# CMD ./run_datomic.sh && java -Dhyperfiddle.datomic.uri="datomic:dev://localhost:4334/mbrainz-full" -cp app.jar clojure.main -m prod