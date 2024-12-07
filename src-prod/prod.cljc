(ns prod
  #?(:cljs (:require-macros prod))
  (:require #?(:clj [clojure.tools.logging :as log])
            [contrib.assert :refer [check]]
            [contrib.template :refer [comptime-resource]]
            #?(:clj [electric-fiddle.server-jetty :refer [start-server!]])
            [hyperfiddle.electric3 :as e]
            #?(:cljs hyperfiddle.electric-client3)

            #?(:clj prod-fiddle-config)
            load-prod-fiddles!))

;#?(:clj (alias 'build (create-ns 'hyperfiddle.fiddle-build)))

(def config
  (merge
    (comptime-resource "electric-manifest.edn") ; prod only, baked into both client and server, nil during build
    {:host "0.0.0.0", :port 8080,
     :resources-path "public"
     :manifest-path "public/js/manifest.edn"})) ; shadow build manifest

#?(:clj (defn ensure-ns-required! "return symbolic-ns only if successfully loaded, else nil"
          [ns-sym] (try (require ns-sym) ns-sym (catch Exception e (prn e) nil))))

#?(:clj
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (alter-var-root #'config #(merge % args))
     (log/info (pr-str config))
     (check string? (:hyperfiddle.fiddle-build/electric-user-version config))
     (let [?user-ns (ensure-ns-required! (symbol (check string? (:hyperfiddle.fiddle-build/fiddle-ns config))))
           ?entrypoint (symbol (name (check ?user-ns)) "ProdMain")]
       ; is there a sensible default ProdMain? user would need to supply a default index fiddle
       (start-server! (eval `(fn [ring-req#] (e/boot-server {} ~?entrypoint (e/server ring-req#))))
         config))))

(defmacro inject-user-main [] (symbol (name prod-fiddle-config/*comptime-prod-fiddle-ns*) "ProdMain"))

#?(:cljs
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} (inject-user-main) (e/server nil))
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; teardown
       (set! reactor nil))))