FROM clojure:temurin-17-tools-deps-1.12.0.1501 AS build
WORKDIR /app
COPY deps.edn deps.edn
ARG VERSION
ENV VERSION=$VERSION
RUN clojure -A:prod -M -e ::ok       # preload – rebuilds if deps or commit version changes
RUN clojure -A:build:prod -M -e ::ok # preload

COPY shadow-cljs.edn shadow-cljs.edn
COPY src src
COPY src-prod src-prod
COPY src-build src-build
COPY resources resources

RUN clojure -X:prod:build uberjar :version "\"$VERSION\"" :build/jar-name "app.jar"

FROM amazoncorretto:17 AS app
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar

EXPOSE 8080
CMD java -cp app.jar clojure.main -m prod