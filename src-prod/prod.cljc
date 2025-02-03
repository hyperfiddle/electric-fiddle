(ns prod
  #?(:cljs (:require-macros [prod :refer [comptime-resource]]))
  (:require clojure.edn
            #?(:clj clojure.java.io)
            #?(:clj [clojure.tools.logging :as log])
            [contrib.assert :refer [check]]
            electric-starter-app.main
            #?(:clj [electric-starter-app.server-jetty :as jetty])
            [hyperfiddle.electric3 :as e]
            #?(:cljs [hyperfiddle.electric-client3])))

(defmacro comptime-resource [filename]
  (some-> filename clojure.java.io/resource slurp clojure.edn/read-string))

(def config
  (merge
    ;; Client and server versions must match in prod (dev is not concerned)
    ;; `src-build/build.clj` will compute the common version and store it in `resources/electric-manifest.edn`
    ;; On prod boot, `electric-manifest.edn`'s content is injected here.
    ;; Server is therefore aware of the program version.
    ;; The client's version is injected in the compiled .js file.
    (comptime-resource "electric-manifest.edn")
    {:host "0.0.0.0", :port 8080,
     :resources-path "public/electric_starter_app"
     ;; shadow build manifest path, to get the fingerprinted main.sha1.js file to ensure cache invalidation
     :manifest-path "public/electric_starter_app/js/manifest.edn"}))

#?(:clj ; server entrypoint
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (alter-var-root #'config #(merge % args))
     (log/info (pr-str config))
     (check string? (:hyperfiddle.electric-ring-adapter3/electric-user-version config))
     (jetty/start-server!
       (fn [ring-req] (e/boot-server {} electric-starter-app.main/Main (e/server ring-req))) ; inject server-only ring-request - symmetric with e/boot-client
       config)))

#?(:cljs ; client entrypoint
   (defn ^:export start! []
     ((e/boot-client {} electric-starter-app.main/Main (e/server (e/amb))) ; symmetric with e/boot-server: same arity - no-value hole in place of server-only ring-request.
      #(js/console.log "Reactor success:" %)
      #(js/console.error "Reactor failure:" %))))
