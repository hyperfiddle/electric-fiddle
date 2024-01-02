(ns electric-fiddle.server-httpkit
  (:require
   [clojure.tools.logging :as log]
   [electric-fiddle.ring-middleware :as middleware]
   [hyperfiddle.electric-httpkit-adapter :as electric-httpkit]
   [org.httpkit.server :as httpkit]
   [ring.middleware.basic-authentication :as auth]
   [ring.middleware.cookies :as cookies]
   [ring.middleware.params :refer [wrap-params]])
  (:import
   (java.io IOException)
   (java.net BindException)))

(defn wrap-reject-stale-client ; TODO move into electric library, has fragile config
  "Intercept websocket UPGRADE request and check if client and server versions
matches. An electric client is allowed to connect if its version matches the
server's version, or if the server doesn't have a version set (dev mode).
Otherwise, the client connection is rejected gracefully."
  [next-handler {:keys [:hyperfiddle.electric/user-version] :as _config}]
  (fn [ring-request]
    (if (:websocket? ring-request)
      (let [client-version (get-in ring-request [:query-params "ELECTRIC_USER_VERSION"])]
        (cond
          (= client-version user-version) (next-handler ring-request)
          (= client-version "hyperfiddle_electric_client__dirty") (next-handler ring-request)
          ;(clojure.string/ends-with? client-version "-dirty") (next-handler ring-request) ; todo read version from runtime config?
          :else (do (log/info 'wrap-reject-stale-client "client:" (pr-str client-version) "server:" (pr-str user-version))
                    (httpkit/as-channel ring-request
                      (electric-httpkit/reject-websocket-handler 1008 "stale client"))))) ; https://www.rfc-editor.org/rfc/rfc6455#section-7.4.1
      (next-handler ring-request))))

(defn wrap-authenticated-request [next-handler]
  (fn [ring-request]
    (if (:websocket? ring-request)
      (auth/basic-authentication-request ring-request middleware/authenticate)
      (next-handler ring-request))))

(defn electric-websocket-middleware [next-handler config entrypoint]
  ;; Applied bottom-up
  (-> (electric-httpkit/wrap-electric-websocket next-handler entrypoint) ; 5. connect electric client
    (wrap-authenticated-request) ; 4. Optional - authenticate before opening a websocket
    (cookies/wrap-cookies) ; 3. makes cookies available to Electric app
    (wrap-reject-stale-client config) ; 2. reject stale electric client
    (wrap-params) ; 1. parse query params
    ))

(defn middleware [config entrypoint]
  (-> (middleware/http-middleware config)  ; 2. serve regular http content
    (electric-websocket-middleware config entrypoint) ; 1. intercept electric websocket
    ))

(defn start-server! [entrypoint
                     {:keys [port host]
                      :or   {port 8080, host "0.0.0.0"} ; insecure default?
                      :as   config}]
  (log/info (pr-str config))
  (try
    (let [server (httpkit/run-server (middleware config entrypoint)
                   (merge {:port   port
                           :max-ws (* 1024 1024 100)} ; max ws message size = 100M, temporary
                         config))]
      (log/info "👉" (str "http://" host ":" port))
      server)

    (catch IOException err
      (if (instance? BindException (ex-cause err))  ; port is already taken, retry with another one
        (do (log/warn "Port" port "was not available, retrying with" (inc port))
          (start-server! entrypoint (update config :port inc)))
        (throw err)))))
