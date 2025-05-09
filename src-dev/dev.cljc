(ns dev
  (:require
   electric-starter-app.main

   #?(:clj [shadow.cljs.devtools.api :as shadow-cljs-compiler])
   #?(:clj [shadow.cljs.devtools.server :as shadow-cljs-compiler-server])
   #?(:clj [clojure.tools.logging :as log])

   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.params :refer [wrap-params]])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [hyperfiddle.electric-ring-adapter3 :as electric-ring])
   ))

(comment (-main)) ; repl entrypoint

#?(:clj ; server entrypoint
   (defn -main [& args]
     (log/info "Starting Electric compiler and server...")

     (shadow-cljs-compiler-server/start!) ; no-op in calva shadow-cljs configuration which starts this out of band
     (shadow-cljs-compiler/watch :dev)

     (def server (ring/run-jetty
                   (-> (fn [ring-request] ; return the dev version of index.html which has no stale client checks
                         (-> (ring-response/resource-response "index.dev.html" {:root "public/electric_starter_app"})
                           (ring-response/content-type "text/html")))
                     (wrap-resource "public/electric_starter_app")
                     (wrap-content-type)
                     ;; electric middleware that configures the app's websocket connection
                     ;; server-side electric process boot happens in this middleware
                     (electric-ring/wrap-electric-websocket (fn [ring-request] (electric-starter-app.main/electric-boot ring-request)))
                     (wrap-params))
                   {:host "localhost", :port 8080, :join? false
                    :configurator (fn [server] ; tune jetty
                                    (org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer/configure
                                      (.getHandler server)
                                      (reify org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer$Configurator
                                        (accept [_this _servletContext wsContainer]
                                          (.setIdleTimeout wsContainer (java.time.Duration/ofSeconds 60))
                                          (.setMaxBinaryMessageSize wsContainer (* 100 1024 1024)) ; 100M - temporary
                                          (.setMaxTextMessageSize wsContainer (* 100 1024 1024))))))}))  ; 100M - temporary
     (log/info "👉 http://localhost:8080")))

(declare browser-process)
#?(:cljs ; client entrypoint
   (defn ^:dev/after-load ^:export -main []
     (set! browser-process
       ;; client-side electric process boot happens here
       ((electric-starter-app.main/electric-boot nil)
        #(js/console.log "Reactor success:" %)
        #(js/console.error "Reactor failure:" %)))))

#?(:cljs
   (defn ^:dev/before-load stop! []
     (when browser-process (browser-process)) ; tear down electric browser process
     (set! browser-process nil)))

(comment
  (shadow-cljs-compiler-server/stop!)
  (.stop server) ; stop jetty server
  )
