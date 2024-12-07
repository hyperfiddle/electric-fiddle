(ns prod
  #?(:cljs (:require-macros [prod :refer [install-user-inject]]))
  (:require #?(:clj [clojure.tools.logging :as log])
            [contrib.assert :refer [check]]
            [contrib.template :refer [comptime-resource]]
            #?(:clj [electric-fiddle.server-jetty :refer [start-server!]])
            [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e]
            #?(:cljs hyperfiddle.electric-client3)
            #?(:cljs #=(clojure.core/identity hyperfiddle/*hyperfiddle-user-ns*)))) ; domain DI here

(def config
  (merge
    (comptime-resource "electric-manifest.edn") ; prod only, baked into both client and server, nil during build
    {:host "0.0.0.0", :port 8080,
     :resources-path "public"
     :manifest-path "public/js/manifest.edn"})) ; shadow build manifest

#?(:clj (defn ensure-sym-ns-required! "return qualified sym only if successfully resolved, else nil"
          [qs] (when (some? (requiring-resolve qs)) qs)))

#?(:clj
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (alter-var-root #'config #(merge % args))
     (log/info (pr-str config))
     (check string? (:build/electric-user-version config))
     (check string? (:build/fiddle-ns config))
     (let [?entrypoint (ensure-sym-ns-required! (symbol (str (:build/fiddle-ns config)) "ProdMain"))]
       ; is there a sensible default ProdMain? user would need to supply a default index fiddle
       (start-server! (eval `(fn [ring-req#] (e/boot-server {} ~?entrypoint (e/server ring-req#))))
         config))))

(defmacro install-user-inject [] (symbol (name hf/*hyperfiddle-user-ns*) "ProdMain"))

#?(:cljs
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} (install-user-inject) (e/server nil))
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; teardown
       (set! reactor nil))))