(ns electric-fiddle.ring-middleware
  "electric-fiddle-specific Ring middleware for auth, routing, and WS gating."
  (:require
   [ring.middleware.basic-authentication :as auth]
   [ring.middleware.cookies :as cookies]
   [ring.util.response :as res]
   [clojure.string :as str]))

(defn authenticate [username _password] username) ; demo (accept-all) authentication

(defn wrap-demo-authentication "A Basic Auth example. Accepts any username/password and store the username in a cookie."
  [next-handler]
  (-> (fn [ring-req]
        (let [res (next-handler ring-req)]
          (if-let [username (:basic-authentication ring-req)]
            (res/set-cookie res "username" username {:http-only true})
            res)))
    (cookies/wrap-cookies)
    (auth/wrap-basic-authentication authenticate)))

(defn wrap-authenticated-request [next-handler]
  (fn [ring-request]
    (next-handler (auth/basic-authentication-request ring-request authenticate))))

(defn wrap-demo-router "A basic path-based routing middleware"
  [next-handler]
  (fn [ring-req]
    (case (:uri ring-req)
      "/auth" (let [response  ((wrap-demo-authentication next-handler) ring-req)]
                (if (= 401 (:status response)) ; authenticated?
                  response                     ; send response to trigger auth prompt
                  (-> (res/status response 302) ; redirect
                    (res/header "Location" (get-in ring-req [:headers "referer"]))))) ; redirect to where the auth originated
      ;; For any other route, delegate to next middleware
      (next-handler ring-req))))

(defn wrap-gate
  "Allows ring requests to pass through to the `next-ring-handler` when `(open-fn? ring-request)` is true.
  Otherwise, return the value of `(on-closed-fn ring-request)`."
  [next-ring-handler {::keys [open-fn? on-closed-fn]
                      :or    {open-fn?     (constantly true)
                              on-closed-fn (constantly {:status 423, :body "Locked"})}}] ; HTTP 423 Locked, browsers interprets it as a generic 400
  (fn [ring-request]
    (if (open-fn? ring-request)
      (next-ring-handler ring-request)
      (on-closed-fn ring-request))))

(defn wrap-allow-ws-connect
  "Allow websocket upgrade requests to pass through if `(accept-ws-connect-fn ring-request)` is true.
  Use case: prevent electric client to connect to a dev server until the server is ready."
  [next-handler accept-ws-connect-fn]
  (wrap-gate next-handler
    {::open-fn?    (if-not accept-ws-connect-fn
                     (constantly true)
                     (fn [ring-request]
                       (if (not (str/blank? (get-in ring-request [:headers "upgrade"])))
                         (accept-ws-connect-fn ring-request)
                         true)))
     ::on-closed-fn (fn [_ring-request] {:status 423 ; HTTP 423 Locked, not using 503 because it's not an error. Browsers interprets 423 as a generic 403.
                                         :body "Server is not ready to accept WS connections. Try again later."})
     }))

