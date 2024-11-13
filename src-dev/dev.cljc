(ns dev
  (:require
   clojure.edn
   electric-starter-app.main
   [hyperfiddle.electric3 :as e :refer [$]]
   #?(:clj [hyperfiddle.entrypoint])
   #?(:cljs [hyperfiddle.electric-client3])
   #?(:clj [electric-starter-app.server-jetty :as jetty])
   #?(:clj [shadow.cljs.devtools.api :as shadow])
   #?(:clj [shadow.cljs.devtools.server :as shadow-server])
   #?(:clj [clojure.tools.logging :as log])))

(comment (-main)) ; repl entrypoint

#?(:clj ;; Server Entrypoint
   (do
     (def config
       (merge
         {:host "localhost"
          :port 8080
          :resources-path "public/electric_starter_app"
          :manifest-path ; contains Electric compiled program's version so client and server stays in sync
          "public//electric_starter_app/js/manifest.edn"}
         (try (clojure.edn/read-string (slurp "resources/config.edn")) (catch java.io.FileNotFoundException _))
         ))

     (defn -main [& args]
       (log/info "Starting Electric compiler and server...")

       (shadow-server/start!)
       (shadow/watch :dev)
       (comment (shadow-server/stop!))

       (def server (jetty/start-server!
                     (fn [ring-request]
                       (hyperfiddle.entrypoint/boot-server {} electric-starter-app.main/Main (e/server ring-request)))
                     config))
       (comment
         (.stop server)
         )
       )))

#?(:cljs ;; Client Entrypoint
   (do
     (defonce reactor nil)

     (defn ^:dev/after-load ^:export start! []
       (set! reactor ((e/boot-client {} electric-starter-app.main/Main (e/server nil))
                       #(js/console.log "Reactor success:" %)
                       #(js/console.error "Reactor failure:" %))))

     (defn ^:dev/before-load stop! []
       (when reactor (reactor)) ; stop the reactor
       (set! reactor nil))))
