(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.tools.logging :as log]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]))

(def electric-user-version (b/git-process {:git-args "describe --tags --long --always --dirty"}))

(defn build-client "
invoke like: clj -X:build:prod build-client`
Note: do not use `clj -T`, because Electric shadow compilation requires
application classpath to be available"
  [{:keys [optimize debug verbose version shadow-build]
    :or {optimize true, debug false, verbose false, version electric-user-version, shadow-build :prod}
    :as config}]
  (log/info 'build-client (pr-str config #_argmap))
  (b/delete {:path "resources/public/electric_starter_app/js"})
  (b/delete {:path "resources/electric-manifest.edn"})

  ; bake electric-user-version into artifact, cljs and clj
  (b/write-file {:path "resources/electric-manifest.edn" :content (-> config (dissoc :version :shadow-build) (assoc :hyperfiddle/electric-user-version version))})

  ; "java.lang.NoClassDefFoundError: com/google/common/collect/Streams" is fixed by
  ; adding com.google.guava/guava {:mvn/version "31.1-jre"} to deps,
  ; see https://hf-inc.slack.com/archives/C04TBSDFAM6/p1692636958361199
  (shadow-server/start!)
  (as->
      (shadow-api/release shadow-build
        {:debug   debug,
         :verbose verbose,
         :config-merge
         [{:compiler-options {:optimizations (if optimize :advanced :simple)}
           :closure-defines  {'hyperfiddle.electric-client3/ELECTRIC_USER_VERSION version}}]})
      shadow-status (assert (= shadow-status :done) "shadow-api/release error")) ; fail build on error
  (shadow-server/stop!)
  (log/info "client built"))

(def class-dir "target/classes")

(defn uberjar
  [{:keys [optimize debug verbose ::jar-name, ::skip-client, version, aliases, shadow-build]
    :or {optimize true, debug false, verbose false, skip-client false, version electric-user-version, aliases [:prod], shadow-build :prod}
    :as args}]
  ; careful, shell quote escaping combines poorly with clj -X arg parsing, strings read as symbols
  (log/info 'uberjar (pr-str args))
  (b/delete {:path "target"})

  (when-not skip-client
    (build-client (select-keys args [:optimize :debug :verbose :version :shadow-build])))

  (b/copy-dir {:target-dir class-dir :src-dirs ["src" "src-prod" "resources"]})
  (let [jar-name (or (some-> jar-name str) ; override for Dockerfile builds to avoid needing to reconstruct the name
                   (format "electric-starter-app-%s.jar" version))]
    (b/uber {:class-dir class-dir
             :uber-file (str "target/" jar-name)
             :basis     (b/create-basis {:project "deps.edn" :aliases aliases})})
    (log/info jar-name)))
