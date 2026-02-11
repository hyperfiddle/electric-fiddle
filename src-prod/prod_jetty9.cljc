(ns prod-jetty9 ; run with `clj -M:prod:jetty9 -m prod-jetty9`
  #?(:cljs (:require-macros [prod-jetty9 :refer [comptime-resource inject-user-main]]))
  (:require
   ;; electric-fiddle: dynamic fiddle-ns resolution
   [contrib.assert :refer [check]]
   #?(:clj [electric-fiddle.ring-middleware :as middleware]) ; electric-fiddle: auth middleware
   #?(:clj prod-fiddle-config)
   load-prod-fiddles!

   ;; standard starter-app requires
   #?(:clj [ring.adapter.jetty :as ring])
   #?(:clj [ring.util.response :as ring-response])
   #?(:clj [ring.middleware.not-modified :refer [wrap-not-modified]])
   #?(:clj [ring.middleware.resource :refer [wrap-resource]])
   #?(:clj [ring.middleware.content-type :refer [wrap-content-type]])
   #?(:clj [ring.middleware.params :refer [wrap-params]])
   #?(:clj [ring.middleware.cookies :as cookies])
   #?(:clj [hyperfiddle.electric-jetty9-ring-adapter3 :as electric-jetty9]) ; jetty 9
   #?(:cljs [hyperfiddle.electric-client3 :as electric-client])

   [hyperfiddle.electric3 :as e]

   #?(:clj clojure.edn)
   #?(:clj clojure.java.io)
   #?(:clj [clojure.tools.logging :as log])))

(defmacro comptime-resource [filename] (some-> filename clojure.java.io/resource slurp clojure.edn/read-string))

(declare wrap-prod-index-page wrap-ensure-cache-bust-on-server-deployment)

;; electric-fiddle: dynamic fiddle-ns resolution
#?(:clj (defn ensure-ns-required! "return symbolic-ns only if successfully loaded, else nil"
          [ns-sym] (try (require ns-sym) ns-sym (catch Exception e (prn e) nil))))

#?(:clj (defn ensure-resolved! "return symbolic-var only if successfully resolved, else nil"
          [var-qualified-sym] (when (some? (resolve var-qualified-sym)) var-qualified-sym)))

(defmacro inject-user-main [] (symbol (name prod-fiddle-config/*comptime-prod-fiddle-ns*) "ProdMain"))

#?(:clj ; server entrypoint
   (defn -main [& {:strs [http-port] :as args}] ; clojure.main entrypoint, args are strings
     (let [config
           ;; Client and server versions must match in prod (dev is not concerned)
           ;; `src-build/build.clj` will compute the common version and store it in `resources/electric-manifest.edn`
           ;; On prod boot, `electric-manifest.edn`'s content is injected here.
           ;; Server is therefore aware of the program version.
           ;; The client's version is injected in the compiled .js file.
           (merge
             (comptime-resource "electric-manifest.edn")
             args
             {:host "0.0.0.0", :port (or (some-> http-port parse-long) 8080),
              :resources-path "public"
              ;; shadow-cljs build manifest path, to get the fingerprinted main.sha1.js file to ensure cache invalidation
              :manifest-path "public/electric_fiddle/js/manifest.edn"})]
       (log/info (pr-str config))
       (assert (string? (:hyperfiddle/electric-user-version config)))
       ;; electric-fiddle: dynamic entrypoint resolution from fiddle-ns
       (let [user-ns (check (ensure-ns-required! (symbol (check string? (:build/fiddle-ns config)))))
             entrypoint (check (ensure-resolved! (symbol (name user-ns) "ProdMain")))]
         (ring/run-jetty
           (-> (fn [ring-request] (-> (ring-response/not-found "Page not found") (ring-response/content-type "text/plain")))
             (wrap-prod-index-page config)                ; serve index.prod.html with injected JS modules
             (wrap-resource (:resources-path config))     ; serve static files from classpath
             (wrap-content-type)
             (wrap-not-modified)
             (wrap-ensure-cache-bust-on-server-deployment)
             (middleware/wrap-demo-router)                 ; electric-fiddle: auth routing
             (middleware/wrap-authenticated-request)       ; electric-fiddle: authenticate
             (cookies/wrap-cookies)                        ; electric-fiddle: makes cookies available
             (wrap-params))                               ; boilerplate – parse request URL parameters
           {:host (:host config), :port (:port config), :join? false
            :configurator (fn [server]
                            (electric-jetty9/electric-jetty9-ws-install server "/"
                              (eval `(fn [ring-req#] (e/boot-server {} ~entrypoint (e/server ring-req#)))) ; boot server-side Electric process
                              (fn [next-handler] ; electric-fiddle: WS middleware
                                (-> next-handler
                                  (middleware/wrap-authenticated-request) ; electric-fiddle: authenticate before opening websocket
                                  (cookies/wrap-cookies)                  ; electric-fiddle: makes cookies available to Electric app
                                  (electric-jetty9/wrap-reject-stale-client config) ; ensures electric client and servers stays in sync
                                  (wrap-params))))
                            ;; Gzip served assets
                            (.setHandler server (doto (new org.eclipse.jetty.server.handler.gzip.GzipHandler)
                                                  (.setMinGzipSize 1024)
                                                  (.setHandler (.getHandler server)))))}))
       )))

#?(:cljs ; client entrypoint
   (defn ^:export -main []
     ;; client-side electric process boot happens here
     ((electric-client/reload-when-stale ; hard-reload the page to fetch new assets when a new server version is deployed
        (e/boot-client {} (inject-user-main) (e/server (e/amb)))))))


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
   (defn wrap-ensure-cache-bust-on-server-deployment [next-handler]
     (fn [ring-req]
       (-> (next-handler ring-req)
         (ring-response/update-header "Cache-Control" (fn [cache-control] (or cache-control "public, max-age=0, must-revalidate")))))))

#?(:clj
   (defn wrap-prod-index-page
     "Serves `index.prod.html` with injected javascript modules from `manifest.edn`.
  `manifest.edn` is generated at client build time and contains javascript modules
  information (e.g. file location and file hash)."
     [next-handler config]
     (fn [ring-req]
       (assert (string? (:resources-path config)))
       (assert (string? (:manifest-path config)))
       (if-let [response (ring-response/resource-response (str (:resources-path config) "/electric_fiddle/index.prod.html"))]
         (if-let [module (get-compiled-javascript-modules (:manifest-path config))]
           (-> (ring-response/response (template (slurp (:body response)) (merge config module)))
             (ring-response/content-type "text/html")
             (ring-response/header "Cache-Control" "no-store")) ; never cache – this is dynamically generated content.
           (-> (ring-response/not-found (pr-str ::missing-shadow-build-manifest)) ; can't inject js modules
             (ring-response/content-type "text/plain")))
         ;; else – index.prod.html was not found on classpath
         (next-handler ring-req)))))
