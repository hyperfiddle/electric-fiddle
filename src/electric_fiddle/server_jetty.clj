(ns electric-fiddle.server-jetty
  (:require
   [clojure.tools.logging :as log]
   [electric-fiddle.ring-middleware :as middleware]
   [hyperfiddle.electric-ring-adapter :as electric-ring]
   [ring.adapter.jetty :as ring]
   [ring.middleware.basic-authentication :as auth]
   [ring.middleware.cookies :as cookies]
   [ring.middleware.params :refer [wrap-params]]
   [ring.websocket :as ws])
  (:import
   (java.io IOException)
   (java.net BindException)
   (org.eclipse.jetty.server.handler.gzip GzipHandler)
   (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer JettyWebSocketServletContainerInitializer$Configurator)))

(defn wrap-reject-stale-client ; TODO move into electric library, has fragile config
  "Intercept websocket UPGRADE request and check if client and server versions
matches. An electric client is allowed to connect if its version matches the
server's version, or if the server doesn't have a version set (dev mode).
Otherwise, the client connection is rejected gracefully."
  [next-handler {:keys [:hyperfiddle.electric/user-version] :as _config}]
  (fn [ring-request]
    (if (ws/upgrade-request? ring-request)
      (let [client-version (get-in ring-request [:query-params "ELECTRIC_USER_VERSION"])]
        (cond
          (= client-version user-version) (next-handler ring-request)
          (= client-version "hyperfiddle_electric_client__dirty") (next-handler ring-request)
          ;(clojure.string/ends-with? client-version "-dirty") (next-handler ring-request) ; todo read version from runtime config?
          :else (do (log/info 'wrap-reject-stale-client "client:" (pr-str client-version) "server:" (pr-str user-version))
                    (electric-ring/reject-websocket-handler 1008 "stale client")))) ; https://www.rfc-editor.org/rfc/rfc6455#section-7.4.1
      (next-handler ring-request))))

(defn wrap-authenticated-request [next-handler]
  (fn [ring-request]
    (if (:websocket? ring-request)
      (auth/basic-authentication-request ring-request middleware/authenticate)
      (next-handler ring-request))))

(defn electric-websocket-middleware [next-handler config entrypoint]
  ;; Applied bottom-up
  (-> (electric-ring/wrap-electric-websocket next-handler entrypoint) ; 5. connect electric client
    (wrap-authenticated-request) ; 4. Optional - authenticate before opening a websocket
    (cookies/wrap-cookies) ; 3. makes cookies available to Electric app
    (wrap-reject-stale-client config) ; 2. reject stale electric client
    (wrap-params) ; 1. parse query params
    ))

(defn middleware [config entrypoint]
  (-> (middleware/http-middleware config)  ; 2. serve regular http content
    (electric-websocket-middleware config entrypoint) ; 1. intercept electric websocket
    ))

(defn- add-gzip-handler!
  "Makes Jetty server compress responses. Optional but recommended."
  [server]
  (.setHandler server
    (doto (GzipHandler.)
      #_(.setIncludedMimeTypes (into-array ["text/css" "text/plain" "text/javascript" "application/javascript" "application/json" "image/svg+xml"])) ; only compress these
      (.setMinGzipSize 1024)
      (.setHandler (.getHandler server)))))

(defn- configure-websocket! [server]
  (JettyWebSocketServletContainerInitializer/configure
    (.getHandler server)
    (reify JettyWebSocketServletContainerInitializer$Configurator
      (accept [_this _servletContext wsContainer]
        (.setIdleTimeout wsContainer (java.time.Duration/ofSeconds 60))
        (.setMaxBinaryMessageSize wsContainer (* 100 1024 1024)) ; 100M
        (.setMaxTextMessageSize wsContainer (* 100 1024 1024))   ; 100M
        ))))

(defn- configure-jetty! [server]
  (configure-websocket! server)
  (add-gzip-handler! server))

(defn start-server! [entrypoint
                     {:keys [port host]
                      :or   {port 8080, host "0.0.0.0"} ; insecure default?
                      :as   config}]
  (log/info (pr-str config))
  (try
    (let [server     (ring/run-jetty (middleware config entrypoint)
                       (merge {:port         port
                               :join?        false
                               :configurator configure-jetty!}
                         config))
          final-port (-> server (.getConnectors) first (.getPort))]
      (log/info "👉" (str "http://" host ":" final-port))
      server)

    (catch IOException err
      (if (instance? BindException (ex-cause err))  ; port is already taken, retry with another one
        (do (log/warn "Port" port "was not available, retrying with" (inc port))
          (start-server! entrypoint (update config :port inc)))
        (throw err)))))
