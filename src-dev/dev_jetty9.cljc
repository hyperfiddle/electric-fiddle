(ns dev-jetty9 ; require :jetty9 deps alias
  (:require
   ;; electric-fiddle: multi-fiddle boot
   [electric-fiddle.fiddle-index :refer [FiddleMain]]
   #?(:clj [electric-fiddle.ring-middleware :as middleware]) ; electric-fiddle: auth middleware
   #?(:clj dev-fiddle-config)
   load-dev-fiddles!

   ;; standard starter-app requires
   #?(:clj [shadow.cljs.devtools.api :as shadow-cljs-compiler])
   #?(:clj [shadow.cljs.devtools.server :as shadow-cljs-compiler-server])
   #?(:clj [clojure.tools.logging :as log])

   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.params :refer [wrap-params]])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [ring.middleware.cookies :as cookies])
   #?(:clj [hyperfiddle.electric-jetty9-ring-adapter3 :refer [electric-jetty9-ws-install]])

   [hyperfiddle.electric3 :as e]
   #?(:cljs hyperfiddle.electric-client3)
   [hyperfiddle.rcf :as rcf]))

;; electric-fiddle: multi-fiddle boot
(e/defn DevMain [ring-req]
  (let [fiddles (merge #?@(:default #=(dev-fiddle-config/comptime-dev-fiddle-indexes)))]
    (FiddleMain ring-req fiddles)))

(comment (-main)) ; repl entrypoint

;; electric-fiddle: pause WS reconnect during shadow-cljs compilation
#?(:clj (def !cljs-is-compiling (atom false)))

#?(:clj
   (defn pause-websocket-reconnect-while-compiling ; Shadow hook registered in `shadow-cljs.edn`
     {:shadow.build/stages #{:compile-prepare :flush}}
     [build-state]
     (case (:shadow.build/stage build-state)
       :compile-prepare (reset! !cljs-is-compiling true)
       :flush (reset! !cljs-is-compiling false)
       nil)
     build-state))

#?(:clj (defn next-available-port-from [start] (first (filter #(try (doto (java.net.ServerSocket. %) .close) % (catch Exception _ (println (format "Port %s already taken" %)) nil)) (iterate inc start)))))

#?(:clj ; server entrypoint
   (defn -main [& args]
     (let [{:keys [http-port]} (first args)
           http-port (or http-port (next-available-port-from 8080))]
       (log/info "Starting Electric compiler and server...")

       (shadow-cljs-compiler-server/start!)
       (shadow-cljs-compiler/watch :dev-jetty9)

       (def server (ring/run-jetty
                     (-> ; ring middlewares – applied bottom up:
                       (fn [ring-request] ; index page fallback
                         (-> (ring-response/resource-response "index.dev.html" {:root "public/electric_fiddle"})
                           (ring-response/content-type "text/html")))
                       (wrap-resource "public")            ; serve assets from disk
                       (wrap-content-type)                  ; boilerplate – to serve assets with correct mime/type
                       (middleware/wrap-demo-router)         ; electric-fiddle: auth routing
                       (middleware/wrap-authenticated-request) ; electric-fiddle: authenticate before opening websocket
                       (cookies/wrap-cookies)                ; electric-fiddle: makes cookies available to Electric app
                       (wrap-params))                       ; boilerplate – parse request URL parameters
                     {:host "0.0.0.0", :port http-port, :join? false
                      :configurator (fn [server]
                                      (electric-jetty9-ws-install server "/"
                                        (fn [ring-request] (e/boot-server {} DevMain (e/server ring-request)))
                                        (fn [next-handler] ; electric-fiddle: WS middleware
                                          (-> next-handler
                                            (middleware/wrap-authenticated-request)
                                            (cookies/wrap-cookies)
                                            (middleware/wrap-allow-ws-connect (fn [_] (not @!cljs-is-compiling))) ; electric-fiddle: gate WS during compilation
                                            (wrap-params)))))}))
       (log/info (format "👉 http://0.0.0.0:%s" http-port)))))

(declare browser-process)
#?(:cljs ; client entrypoint
   (defn ^:dev/after-load ^:export -main []
     (set! browser-process
       ((e/boot-client {} DevMain (e/server (e/amb))))))) ; boot client-side Electric process

#?(:cljs
   (defn ^:dev/before-load stop! [] ; for hot code reload at dev time
     (when browser-process (browser-process)) ; tear down electric browser process
     (set! browser-process nil)))

(comment
  (shadow-cljs-compiler-server/stop!)
  (.stop server) ; stop jetty server
  )
