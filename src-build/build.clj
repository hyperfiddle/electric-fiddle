(ns build
  (:require [clojure.tools.logging :as log]
            [clojure.tools.build.api :as b]
            [contrib.assert :refer [check]]
            contrib.ednish
            [hyperfiddle :as hf]
            [shadow.cljs.devtools.api :as shadow-api]
            [shadow.cljs.devtools.server :as shadow-server]
            [clojure.string :as str]))

(def electric-user-version (b/git-process {:git-args "describe --tags --long --always --dirty"}))

(defn build-client
  "build Electric app client, invoke with -X e.g. 
`clojure -X:build:prod:hello-fiddle build-client :hyperfiddle/fiddle-ns hello-fiddle :debug true`
Note: Electric shadow compilation requires application classpath to be available, 
so do not use `clj -T`"
  ; No point in sheltering shadow from app classpath, shadow loads it anyway!
  [argmap] ; invoke with -X
  (let [{:keys [::fiddle-ns optimize debug verbose]
         :or {optimize true, debug false, verbose false}
         :as config}
        (-> argmap 
          (update ::fiddle-ns str) ; coerce, -X under bash evals as symbol unless shell quoted like '"'foo'"'
          (assoc :hyperfiddle.electric/user-version electric-user-version))]
    (log/info `build-client (pr-str argmap))
    (b/delete {:path "resources/public/js"})
    (b/delete {:path "resources/electric-manifest.edn"})
    
    ; bake domain and user-version into artifact, cljs and clj
    (b/write-file {:path "resources/electric-manifest.edn" :content config}) ; even used?
    
    ; "java.lang.NoClassDefFoundError: com/google/common/collect/Streams" is fixed by
    ; adding com.google.guava/guava {:mvn/version "31.1-jre"} to deps, 
    ; see https://hf-inc.slack.com/archives/C04TBSDFAM6/p1692636958361199
    (shadow-server/start!)
    (binding [hf/*hyperfiddle-user-ns* (symbol (check string? fiddle-ns))]
      (as->
        (shadow-api/release :prod
          {:debug debug,
           :verbose verbose,
           :config-merge
           [{:compiler-options {:optimizations (if optimize :advanced :simple)}
             :closure-defines {'hyperfiddle.electric-client3/ELECTRIC_USER_VERSION electric-user-version}}]})
        shadow-status (assert (= shadow-status :done) "shadow-api/release error"))) ; fail build on error
    (shadow-server/stop!)
    (log/info "client built for fiddle-ns: " fiddle-ns)))

(def class-dir "target/classes")

(defn domain->dir [domain]
  (str/replace (str domain) #"-" "_"))

(defn uberjar
  [{:keys [::fiddle-ns ; shell string read as symbol
           ::fiddle-deps-alias ; shell string read as symbol (NOT keyword)
           optimize debug verbose ::jar-name, ::skip-client]
    :or {optimize true, debug false, verbose false, skip-client false}
    :as args}]
  ; careful, shell quote escaping combines poorly with clj -X arg parsing, strings read as symbols
  (log/info `uberjar (pr-str args))
  (b/delete {:path "target"})

  (when-not skip-client
    (build-client {::fiddle-ns (check some? fiddle-ns) ; pass unparsed, build-client also can be invoked from shell
                   :optimize optimize, :debug debug, :verbose verbose}))

  (b/copy-dir {:target-dir class-dir :src-dirs ["src-framework" "src-prod" "resources"
                                                "src"]}) ; allow code sharing across fiddle domains
  #_(b/copy-dir {:target-dir (str class-dir "/" (domain->dir fiddle-domain)) :src-dirs [(str "src/" (domain->dir fiddle-domain))]})
  (let [jar-name (or (some-> jar-name str) ; override for Dockerfile builds to avoid needing to reconstruct the name
                   (format "target/electricfiddle-%s-%s.jar" (contrib.ednish/encode (str fiddle-ns)) electric-user-version))
        aliases [:prod (keyword (name (check some? fiddle-deps-alias)))]]
    (log/info `uberjar "included aliases:" aliases)
    (b/uber {:class-dir class-dir
             :uber-file jar-name
             :basis     (b/create-basis {:project "deps.edn" :aliases aliases})})
    (log/info jar-name)))

; clj -A:prod:hello-fiddle -M -e ::ok 
; clj -A:build:prod:hello-fiddle -M -e ::ok
; clj -X:build:prod:hello-fiddle build-client :hyperfiddle/fiddle-ns hello-fiddle :debug true
; clj -X:build:prod:hello-fiddle uberjar :hyperfiddle/fiddle-ns hello-fiddle :build/deps-alias :hello-fiddle :debug true
; java -cp target/electricfiddle-hello-fiddle-77ebb18-dirty.jar clojure.main -m prod
