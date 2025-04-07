FROM clojure:openjdk-11-tools-deps AS build
WORKDIR /app

COPY deps.edn deps.edn
RUN clojure -A:build:prod -M -e ::ok   # preload and cache dependencies, only reruns if deps.edn changes

# electric-user-version is computed from git sha during clj build
COPY shadow-cljs.edn shadow-cljs.edn
COPY src src
COPY src-build src-build
COPY src-prod src-prod
COPY resources resources

ARG VERSION
ENV VERSION=$VERSION

RUN clojure -X:build:prod uberjar :version "\"$VERSION\"" :build/jar-name "app.jar"

CMD java -cp target/app.jar clojure.main -m prod