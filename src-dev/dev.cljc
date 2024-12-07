(ns dev
  (:require
    #?(:clj [clojure.tools.logging :as log])
    [electric-fiddle.fiddle-index :refer [FiddleRoot FiddleIndex]]
    [hyperfiddle.electric3 :as e]
    #?(:cljs [hyperfiddle.electric-client3])
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.router3 :as r]
    [hyperfiddle.rcf :as rcf]

    #?(:clj dev-fiddle-config)
    load-dev-fiddles!))

(comment (-main)) ; repl entrypoint

(e/defn DevMain [ring-req]
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body]
    (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (r/router (r/HTML5-History)
        (let [fiddles (merge #?@(:default #=(dev-fiddle-config/comptime-dev-fiddle-indexes)))]
          (FiddleRoot (merge {`FiddleIndex FiddleIndex} fiddles)))))))

#?(:clj
   (do
     ; lazy load heavy dependencies for faster REPL startup
     (def shadow-start! (delay @(requiring-resolve 'shadow.cljs.devtools.server/start!)))
     (def shadow-stop! (delay @(requiring-resolve 'shadow.cljs.devtools.server/stop!)))
     (def shadow-watch (delay @(requiring-resolve 'shadow.cljs.devtools.api/watch)))
     (def start-server! (delay @(requiring-resolve 'electric-fiddle.server-jetty/start-server!)))     ; jetty
     #_(def start-server! (delay @(requiring-resolve 'electric-fiddle.server-httpkit/start-server!))) ; require `:httpkit` deps alias

     (def config
       {:host "0.0.0.0", :port 8080,
        :resources-path "public"
        :manifest-path "public/js/manifest.edn"}) ; shadow build manifest

     (declare server)

     (def !cljs-is-compiling (atom false)) ; tracks if shadow watch is compiling

     (defn pause-websocket-reconnect-while-compiling ; Shadow hook registered in `shadow-cljs.edn`
       {:shadow.build/stages #{:compile-prepare :flush}}
       [build-state]
       (case (:shadow.build/stage build-state)
         :compile-prepare (reset! !cljs-is-compiling true)
         :flush (reset! !cljs-is-compiling false)
         nil)
       build-state)

     (defn -main [& [extra-config]]
       (alter-var-root #'config #(merge % extra-config))
       (log/info (pr-str config))
       (log/info "Starting Electric compiler and server...") ; run after REPL redirects stdout

       (@shadow-start!) ; no-op in calva shadow-cljs configuration which starts this out of band
       (@shadow-watch :dev)
                                        ; todo block until finished?
       (comment (@shadow-stop!))
       (def server (@start-server! (fn [ring-req] (e/boot-server {} DevMain (e/server ring-req)))
                    (assoc config :electric-fiddle.ring-middleware/accept-ws-connect-fn (fn [_] (not @!cljs-is-compiling)))))
       (comment
         (.stop server) ; jetty
         (server)       ; httpkit
         )

       #_(rcf/enable!)
       )

     ;; autostart
     #_(future (-main)) ; wrapped in future to not block user REPL
     ))

#?(:cljs
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} DevMain (e/server nil)) ; to match program shape for (e/server ring-req)
                       #(js/console.log "Reactor success:" %)
                       #(js/console.error "Reactor failure:" %)))
       (hyperfiddle.rcf/enable!))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; teardown
       (set! reactor nil))))

(comment
  "CI tests"
  (alter-var-root #'hyperfiddle.rcf/*generate-tests* (constantly false))
  (hyperfiddle.rcf/enable!)
  (require 'clojure.test)
  (clojure.test/run-all-tests #"(hyperfiddle.api|user.orders)"))

(comment
  "Performance profiling, use :profile deps alias"
  (require '[clj-async-profiler.core :as prof])
  (prof/serve-files 8082)
            ;; Navigate to http://localhost:8082
  (prof/start {:framebuf 10000000})
  (prof/stop))
