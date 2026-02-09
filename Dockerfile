FROM node:18-bookworm-slim AS node-deps
WORKDIR /app
COPY package.json package.json
RUN npm install

FROM clojure:temurin-17-tools-deps-1.12.0.1501 AS build
WORKDIR /app
COPY --from=node-deps /app/node_modules /app/node_modules
COPY deps.edn deps.edn

ARG HYPERFIDDLE_FIDDLE_DEPS_ALIAS
ARG HYPERFIDDLE_FIDDLE_NS
ARG VERSION

ENV VERSION=$VERSION

RUN clojure -A:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok         # preload
RUN clojure -A:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS -M -e ::ok   # preload

COPY shadow-cljs.edn shadow-cljs.edn
COPY src src
COPY src-build src-build
COPY src-prod src-prod
COPY resources resources

RUN clojure -X:build:prod:$HYPERFIDDLE_FIDDLE_DEPS_ALIAS uberjar \
    :build/fiddle-ns $HYPERFIDDLE_FIDDLE_NS \
    :build/fiddle-deps-alias $HYPERFIDDLE_FIDDLE_DEPS_ALIAS \
    :build/jar-name app.jar \
    :version "\"$VERSION\""

FROM amazoncorretto:17 AS app
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080
CMD java -cp app.jar clojure.main -m prod