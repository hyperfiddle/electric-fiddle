(ns dev ; jetty 10+ – the default
  (:require
   ;; electric-fiddle: multi-fiddle boot
   [electric-fiddle.fiddle-index :refer [FiddleMain]]
   #?(:clj [electric-fiddle.ring-middleware :as middleware]) ; electric-fiddle: auth middleware
   #?(:clj dev-fiddle-config)
   load-dev-fiddles!

   ;; standard starter-app requires
   #?(:clj [shadow.cljs.devtools.api :as shadow-cljs-compiler])
   #?(:clj [shadow.cljs.devtools.server :as shadow-cljs-compiler-server])
   #?(:clj [shadow.cljs.devtools.config :as shadow-config])
   #?(:clj [clojure.tools.logging :as log])

   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.params :refer [wrap-params]])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [ring.middleware.cookies :as cookies])
   #?(:clj [hyperfiddle.electric-ring-adapter3 :as electric-ring])
   #?(:clj [hyperfiddle.navigator-agent :as agent])
   #?(:clj [dustingetz.nav-demos :as nav-demos])
   #?(:clj [hyperfiddle.hfql-repl :as hfql-repl])

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
     (let [{:keys [http-port shadow-port nrepl-port agent-port]} (first args)
           ;; Each port auto-finds next free one from its default, so parallel worktrees just work
           http-port   (or http-port   (next-available-port-from 8080))
           shadow-port (or shadow-port (next-available-port-from 9630))
           agent-port  (or agent-port  (next-available-port-from 8090))
           ;; nREPL: let shadow-cljs bind it (port 0 = OS-assigned free port).
           ;; We can't reliably probe-then-pass because shadow binds asynchronously.
           ;; Shadow writes the actual port to .shadow-cljs/nrepl.port for editor discovery.
           nrepl-port  (or nrepl-port 0)]
       (log/info "Starting Electric compiler and server...")

       ;; Load shadow-cljs.edn then override ports so each worktree gets its own
       (shadow-cljs-compiler-server/start!
         (assoc (shadow-config/load-cljs-edn)
           :http  {:port shadow-port}
           :nrepl {:port nrepl-port}))
       (shadow-cljs-compiler/watch :dev)

       (def server (ring/run-jetty
                     (-> ; ring middlewares – applied bottom up:
                       (fn [ring-request] ; index page fallback
                         (-> (ring-response/resource-response "index.dev.html" {:root "public/electric_fiddle"})
                           (ring-response/content-type "text/html")))
                       (wrap-resource "public")            ; serve assets from disk
                       (wrap-content-type)                  ; boilerplate – to serve assets with correct mime/type
                       (middleware/wrap-demo-router)         ; electric-fiddle: auth routing
                       (electric-ring/wrap-electric-websocket ; install Electric server
                         (fn [ring-request] (e/boot-server {} DevMain (e/server ring-request)))) ; boot server-side Electric process
                       (middleware/wrap-authenticated-request) ; electric-fiddle: authenticate before opening websocket
                       (cookies/wrap-cookies)                ; electric-fiddle: makes cookies available to Electric app
                       (middleware/wrap-allow-ws-connect (fn [_] (not @!cljs-is-compiling))) ; electric-fiddle: gate WS during compilation
                       (wrap-params))                       ; boilerplate – parse request URL parameters
                     {:host "0.0.0.0", :port http-port, :join? false
                      :ws-idle-timeout (* 60 1000)          ; 60 seconds in milliseconds
                      :ws-max-binary-size (* 100 1024 1024) ; 100MB - for demo
                      :ws-max-text-size (* 100 1024 1024)}))  ; 100MB - for demo

       ;; Start nav demo agents (local proxy)
       (try
         (let [sitemaps nav-demos/sitemap]
           (def demo-agent (agent/serve! sitemaps nil :port agent-port))
           (hfql-repl/set-ctx! {:sitemap sitemaps}))
         (catch Exception e (println "[user] Nav demo agents failed:" (.getMessage e)))))))

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

;; Auto-connect REPL agent to production cloud proxy
(comment
  #?(:clj
     (try
       (def repl-agent (agent/repl-agent! {} #()))
       (comment (agent/disconnect! repl-agent))
       (catch Exception e (def debug-e e) (println "[user] REPL agent failed, error at: " `debug-e)))))
