(ns build
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.build.api :as b]
            [clojure.tools.logging :as log]
            [dustingetz.check :refer [check]]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]
            prod-fiddle-config))

(def electric-user-version (b/git-process {:git-args "describe --tags --long --always --dirty"}))

(defn build-client "
invoke like: clj -X:build:prod build-client`
Note: do not use `clj -T`, because Electric shadow compilation requires
application classpath to be available"
  [{:keys [::fiddle-ns optimize debug verbose version shadow-build]
    :or {optimize true, debug false, verbose false, version electric-user-version, shadow-build :prod}
    :as config}]
  (let [{:keys [::fiddle-ns] :as config}
        (-> config
          (update ::fiddle-ns (comp not-empty str)) ; coerce, -X under bash evals as symbol unless shell quoted
          (dissoc ::version :shadow-build)
          (assoc :hyperfiddle/electric-user-version version))]
    (log/info 'build-client (pr-str config))
    (b/delete {:path "resources/public/electric_fiddle/js"})
    (b/delete {:path "resources/electric-manifest.edn"})

    ; bake fiddle-ns and electric-user-version into artifact, cljs and clj
    (b/write-file {:path "resources/electric-manifest.edn" :content config}) ; read in prod

    (shadow-server/start!)
    (binding [prod-fiddle-config/*comptime-prod-fiddle-ns* (symbol (check string? fiddle-ns))]
      (as->
        (shadow-api/release shadow-build
          {:debug debug,
           :verbose verbose,
           :config-merge
           [{:compiler-options {:optimizations (if optimize :advanced :simple)}
             :closure-defines {'hyperfiddle.electric-client3/ELECTRIC_USER_VERSION version}}]})
        shadow-status (assert (= shadow-status :done) "shadow-api/release error")))
    (shadow-server/stop!)
    (log/info "client built for fiddle-ns: " fiddle-ns)))

(def class-dir "target/classes")

(defn uberjar
  [{:keys [::fiddle-ns ; shell string read as symbol
           ::fiddle-deps-alias ; shell string read as symbol (NOT keyword)
           optimize debug verbose ::jar-name, ::skip-client
           version aliases shadow-build]
    :or {optimize true, debug false, verbose false, skip-client false, version electric-user-version,
         aliases [:prod], shadow-build :prod}
    :as args}]
  ; careful, shell quote escaping combines poorly with clj -X arg parsing, strings read as symbols
  (log/info 'uberjar (pr-str args))
  (b/delete {:path "target"})

  (when-not skip-client
    (build-client {::fiddle-ns (check some? fiddle-ns) ; pass unparsed, build-client also can be invoked from shell
                   :optimize optimize, :debug debug, :verbose verbose
                   :version version, :shadow-build shadow-build}))

  (let [jar-name (or (some-> jar-name str) ; override for Dockerfile builds to avoid needing to reconstruct the name
                   (format "electric-fiddle-%s.jar" version))
        aliases (into aliases [(keyword (name (check some? fiddle-deps-alias)))])
        basis (b/create-basis {:project "deps.edn" :aliases aliases})]
    (log/info 'uberjar "included aliases:" aliases)
    ;; Copy all source/resource dirs from basis classpath into class-dir.
    ;; (:paths basis) only has base project paths; alias :extra-paths and
    ;; :local/root src dirs appear in :classpath-roots. Filter to directories
    ;; (skip jars — b/uber handles those from the basis).
    ;; Copy dependency dirs first, then this project's dirs last so our files
    ;; (e.g. electric-manifest.edn) take priority over deps with same-named files.
    (let [all-dirs (filterv #(.isDirectory (io/file %)) (:classpath-roots basis))
          project-dirs (set (concat (:paths basis) (get-in basis [:argmap :extra-paths])))
          dep-dirs (filterv (complement project-dirs) all-dirs)
          own-dirs (filterv project-dirs all-dirs)]
      (log/info 'uberjar "copying dep src-dirs:" dep-dirs)
      (log/info 'uberjar "copying project src-dirs:" own-dirs)
      (b/copy-dir {:target-dir class-dir :src-dirs dep-dirs})
      (b/copy-dir {:target-dir class-dir :src-dirs own-dirs}))
    (b/uber {:class-dir class-dir
             :uber-file (str "target/" jar-name)
             :basis     basis})
    (log/info jar-name)))

; clj -X:build:prod:electric-tutorial build-client :build/fiddle-ns docs-site.sitemap :debug true
; clj -X:build:prod:electric-tutorial uberjar :build/fiddle-ns docs-site.sitemap :build/fiddle-deps-alias :electric-tutorial :debug true
; java -cp target/electric-fiddle-*.jar clojure.main -m prod
