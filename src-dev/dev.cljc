(ns dev ; jetty 10+ – the default
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
   #?(:clj [hyperfiddle.electric-ring-adapter3 :as electric-ring])))

(comment (-main)) ; repl entrypoint

#?(:clj ; server entrypoint
   (defn -main [& args]
     (log/info "Starting Electric compiler and server...")

     (shadow-cljs-compiler-server/start!)
     (shadow-cljs-compiler/watch :dev)

     (def server (ring/run-jetty
                   (-> ; ring middlewares – applied bottom up:
                     (fn [ring-request] ; 5. index page fallback
                         (-> (ring-response/resource-response "index.dev.html" {:root "public/electric_starter_app"})
                           (ring-response/content-type "text/html")))
                     (wrap-resource "public") ; 4. serve assets from disk.
                     (wrap-content-type) ; 3. boilerplate – to server assets with correct mime/type.
                     (electric-ring/wrap-electric-websocket ; 2. install Electric server.
                       (fn [ring-request] (electric-starter-app.main/electric-boot ring-request))) ; boot server-side Electric process
                     (wrap-params)) ; 1. boilerplate – parse request URL parameters.
                   {:host "0.0.0.0", :port 8080, :join? false
                    :ws-idle-timeout (* 60 1000)          ; 60 seconds in milliseconds
                    :ws-max-binary-size (* 100 1024 1024) ; 100MB - for demo
                    :ws-max-text-size (* 100 1024 1024)}))  ; 100M - for demo.
     (log/info "👉 http://0.0.0.0:8080")))

(declare browser-process)
#?(:cljs ; client entrypoint
   (defn ^:dev/after-load ^:export -main []
     (set! browser-process
       ((electric-starter-app.main/electric-boot nil)))))  ; boot client-side Electric process

#?(:cljs
   (defn ^:dev/before-load stop! [] ; for hot code reload at dev time
     (when browser-process (browser-process)) ; tear down electric browser process
     (set! browser-process nil)))

(comment
  (shadow-cljs-compiler-server/stop!)
  (.stop server) ; stop jetty server
  )
