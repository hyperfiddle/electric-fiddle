(ns dev-jetty9 ; require :jetty9 deps alias
  (:require
   electric-starter-app.main

   #?(:clj [shadow.cljs.devtools.api :as shadow-cljs-compiler])
   #?(:clj [shadow.cljs.devtools.server :as shadow-cljs-compiler-server])
   #?(:clj [clojure.tools.logging :as log])

   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [hyperfiddle.electric-jetty9-ring-adapter3 :refer [electric-jetty9-ws-install]])
   )
  (:import [missionary Cancelled]))

(comment (-main)) ; repl entrypoint

#?(:clj ; server entrypoint
   (defn -main [& args]
     (log/info "Starting Electric compiler and server...")

     (shadow-cljs-compiler-server/start!)
     (shadow-cljs-compiler/watch :dev)

     (def server (ring/run-jetty
                   (-> ; ring middlewares – applied bottom up:
                     (fn [ring-request] ; 3. index page fallback
                         (-> (ring-response/resource-response "index.dev.html" {:root "public/electric_starter_app"})
                           (ring-response/content-type "text/html")))
                     (wrap-resource "public") ; 2. serve assets from disk.
                     (wrap-content-type)) ; 1. boilerplate – to server assets with correct mime/type.
                   {:host "0.0.0.0", :port 8080, :join? false
                    :configurator (fn [server] ; tune jetty
                                    (electric-jetty9-ws-install server "/" (fn [ring-request] (electric-starter-app.main/electric-boot ring-request))))}))
     (log/info "👉 http://0.0.0.0:8080")))

(declare browser-process)
#?(:cljs ; client entrypoint
   (defn ^:dev/after-load ^:export -main []
     (set! browser-process
       ((electric-starter-app.main/electric-boot nil) ; boot client-side Electric process
        #(js/console.log "Reactor success:" %)
        #(when-not (instance? Cancelled %) (js/console.error "Reactor failure:" %))))))

#?(:cljs
   (defn ^:dev/before-load stop! [] ; for hot code reload at dev time
     (when browser-process (browser-process)) ; tear down electric browser process
     (set! browser-process nil)))

(comment
  (shadow-cljs-compiler-server/stop!)
  (.stop server) ; stop jetty server
  )
