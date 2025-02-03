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

#?(:clj (defn ensure-resolved! "return symbolic-var only if successfully resolved, else nil"
          [var-qualified-sym] (when (some? (resolve var-qualified-sym)) var-qualified-sym)))

#?(:clj
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (alter-var-root #'config #(merge % args))
     (log/info (pr-str config))
     (check string? (:hyperfiddle.electric-ring-adapter3/electric-user-version config))
     (let [user-ns (check (ensure-ns-required! (symbol (check string? (:hyperfiddle.fiddle-build/fiddle-ns config)))))
           entrypoint (check (ensure-resolved! (symbol (name user-ns) "ProdMain")))] ; magic name
       ; todo use fiddle-index by default in prod to eliminate userland boilerplate
       ; (or #_(ensure-resolved! (symbol electric-fiddle.fiddle-index/DevMain)))
       (start-server! (eval `(fn [ring-req#] (e/boot-server {} ~entrypoint (e/server ring-req#)))) ; inject server-only ring-request - symmetric with e/boot-client
         config))))

(defmacro inject-user-main [] (symbol (name prod-fiddle-config/*comptime-prod-fiddle-ns*) "ProdMain"))

#?(:cljs
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} (inject-user-main) (e/server (e/amb))) ; symmetric with e/boot-server: same arity - no-value hole in place of server-only ring-request
                      #(js/console.log "Reactor success:" %)
                      #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; teardown
       (set! reactor nil))))