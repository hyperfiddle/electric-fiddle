(ns dev
  (:require
   #?(:clj [clojure.tools.logging :as log])
   #?(:clj [electric-fiddle.server-jetty :as jetty])
   #?(:clj [shadow.cljs.devtools.api :as shadow])
   #?(:clj [shadow.cljs.devtools.server :as shadow-server])
   #?(:clj config)
   #?(:cljs [hyperfiddle.electric-client-de])
   ;; fiddles
   [hyperfiddle.electric-de :as e]
   ;; [hyperfiddle.rcf :as rcf]
   repros
   ))

(comment (-main)) ; repl entrypoint

#?(:clj
   (do
     ; lazy load heavy dependencies for faster REPL startup

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

       (shadow-server/start!)
       (shadow/watch :dev)
                                        ; todo block until finished?
       (comment (shadow-server/stop!))
       (def server (jetty/start-server! (fn [ring-req] (e/boot-server {} repros/Entrypoint ring-req))
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
     (def electric-entrypoint
       ; in dev, we setup a merged fiddle config,
       ; fiddles must all opt in to the shared routing strategy
       (e/boot-client {} repros/Entrypoint nil))

     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor (electric-entrypoint
                       #(js/console.log "Reactor success:" %)
                       #(js/console.error "Reactor failure:" %)))
       #_(hyperfiddle.rcf/enable!))

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
