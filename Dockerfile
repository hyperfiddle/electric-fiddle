FROM node:14.7-stretch AS node-deps
WORKDIR /app
COPY package.json package.json
RUN npm install

FROM clojure:openjdk-11-tools-deps AS build
WORKDIR /app
COPY --from=node-deps /app/node_modules /app/node_modules
# electric-user-version is computed from git sha during clj build
COPY .git .git
#COPY .m2 /root/.m2
COPY shadow-cljs.edn shadow-cljs.edn
COPY deps.edn deps.edn
COPY src src
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
    :hyperfiddle.fiddle-build/jar-name user.jar

CMD java -cp target/user.jar clojure.main -m prod