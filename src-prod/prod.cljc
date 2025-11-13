(ns prod
  #?(:cljs (:require-macros [prod :refer [comptime-resource]]))
  (:require
   electric-starter-app.main

   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.params :refer [wrap-params]])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [hyperfiddle.electric-ring-adapter3 :as electric-ring])
   #?(:cljs [hyperfiddle.electric-client3 :as electric-client])

   #?(:clj clojure.edn)
   #?(:clj clojure.java.io)
   #?(:clj [clojure.tools.logging :as log])
   ))

(defmacro comptime-resource [filename] (some-> filename clojure.java.io/resource slurp clojure.edn/read-string))

(declare wrap-prod-index-page)

#?(:clj ; server entrypoint
   (defn -main [& {:strs [] :as args}] ; clojure.main entrypoint, args are strings
     (let [config
           ;; Client and server versions must match in prod (dev is not concerned)
           ;; `src-build/build.clj` will compute the common version and store it in `resources/electric-manifest.edn`
           ;; On prod boot, `electric-manifest.edn`'s content is injected here.
           ;; Server is therefore aware of the program version.
           ;; The client's version is injected in the compiled .js file.
           (merge
             (comptime-resource "electric-manifest.edn")
             {:host "0.0.0.0", :port 8080,
              :resources-path "public"
              ;; shadow-cljs build manifest path, to get the fingerprinted main.sha1.js file to ensure cache invalidation
              :manifest-path "public/electric_starter_app/js/manifest.edn"})]
       (log/info (pr-str config))
       (assert (string? (:hyperfiddle/electric-user-version config)))
       (ring/run-jetty
         (-> (fn [ring-request] (-> (ring-response/not-found "Page not found") (ring-response/content-type "text/plain")))
           (wrap-prod-index-page config) ; defined below
           (wrap-resource (:resources-path config))
           (wrap-content-type)
           (electric-ring/wrap-electric-websocket (fn [ring-request] (electric-starter-app.main/electric-boot ring-request)))
           (electric-ring/wrap-reject-stale-client config) ; ensures electric client and servers stays in sync.
           (wrap-params))
         {:host (:host config), :port (:port config), :join? false
          :configurator (fn [server] ; Tune limits
                          (org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer/configure
                            (.getHandler server)
                            (reify org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer$Configurator
                              (accept [_this _servletContext wsContainer]
                                (.setIdleTimeout wsContainer (java.time.Duration/ofSeconds 60))
                                (.setMaxBinaryMessageSize wsContainer (* 100 1024 1024))  ; 100M - for demo
                                (.setMaxTextMessageSize wsContainer (* 100 1024 1024))))) ; 100M - for demo
                          ;; Gzip served assets
                          (.setHandler server (doto (new org.eclipse.jetty.server.handler.gzip.GzipHandler)
                                                (.setMinGzipSize 1024)
                                                (.setHandler (.getHandler server)))))}))))

#?(:cljs ; client entrypoint
   (defn ^:export -main []
     ;; client-side electric process boot happens here
     ((electric-client/reload-when-stale ; hard-reload the page to fetch new assets when a new server version is deployed
        (electric-starter-app.main/electric-boot nil))  ; boot client-side Electric process
      #(js/console.log "Reactor success:" %)
      #(js/console.error "Reactor failure:" %))))


#?(:clj
   (defn template
     "In string template `\"<div>$:foo/bar$</div>\"`, replace all instances of $key$
  with target specified by map `m`. Target values are coerced to string with `str`.
  E.g. (template \"<div>$:foo$</div>\" {:foo 1}) => \"<div>1</div>\" - 1 is coerced to string."
     [t m] (reduce-kv (fn [acc k v] (clojure.string/replace acc (str "$" k "$") (str v))) t m)))

#?(:clj
   (defn get-compiled-javascript-modules [manifest-path]
     (when-let [manifest (clojure.java.io/resource manifest-path)]
       (let [manifest-folder (when-let [folder-name (second (rseq (clojure.string/split manifest-path #"\/")))]
                               (str folder-name "/"))]
         (->> (slurp manifest)
           (clojure.edn/read-string)
           (reduce (fn [r module] (assoc r (keyword "hyperfiddle.client.module" (name (:name module)))
                                    (str manifest-folder (:output-name module)))) {}))))))

#?(:clj
   (defn wrap-prod-index-page
     "Serves `index.prod.html` with injected javascript modules from `manifest.edn`.
  `manifest.edn` is generated at client build time and contains javascript modules
  information (e.g. file location and file hash)."
     [next-handler config]
     (fn [ring-req]
       (assert (string? (:resources-path config)))
       (assert (string? (:manifest-path config)))
       (if-let [response (ring-response/resource-response (str (:resources-path config) "/electric_starter_app/index.prod.html"))]
         (if-let [module (get-compiled-javascript-modules (:manifest-path config))]
           (-> (ring-response/response (template (slurp (:body response)) (merge config module)))
             (ring-response/content-type "text/html")
             (ring-response/header "Cache-Control" "no-store")
             (ring-response/header "Last-Modified" (get-in response [:headers "Last-Modified"])))
           (-> (ring-response/not-found (pr-str ::missing-shadow-build-manifest)) ; can't inject js modules
             (ring-response/content-type "text/plain")))
         ;; else – index.prod.html wasn't not found on classpath
         (next-handler ring-req)))))
