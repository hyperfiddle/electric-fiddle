(ns dustingetz.nav-auth0
  (:require
    clj-http.client
    [dustingetz.caching-http-client :as http]
    clojure.data.json
    cheshire.core
    [clojure.spec.alpha :as s]
    [clojure.walk :refer [keywordize-keys]]
    [contrib.data :refer [auto-props]]
    [contrib.template :refer [comptime-resource]]
    [contrib.assert :refer [check]]
    [hyperfiddle.hfql0 :as hfql]))

; https://auth0.com/docs/api/management/v2
(defprotocol Entity
  (touch [o]))

(defprotocol Auth0
  (get-management-token [o])
  (users [o])
  (user [o user-id])
  (export-users [o ?opts])
  (check-job-status [o job-id])
  (download-export [o url])
  (search-users [o q])) ; Added for incremental updates

(defrecord Auth0Entity [client])

(deftype Auth0Client [config !okhttp ^:unsynchronized-mutable token]
  Entity (touch [o] (locking o (when-not (.-token o) (set! (.-token o) (get-management-token o)))) o)

  Auth0
  (get-management-token [o]
    (let [{:keys [status body]} (clj-http.client/post (format "https://%s/oauth/token" (::domain config))
                                  {:form-params {:grant_type "client_credentials"
                                                 :client_id (::client-id config)
                                                 :client_secret (::client-secret config)
                                                 :audience (::audience config)}
                                   :content-type :application/x-www-form-urlencoded
                                   :as :json})]
      (assert (= status 200))
      (:access_token body)))

  (users [o]
    (letfn [(step [page]
              (let [{::http/keys [status body]}
                    (http/caching-get! !okhttp (format "https://%s/api/v2/users" (::domain config))
                      {:headers {"Authorization" (format "Bearer %s" (.-token o))}
                       :query-params {:page page :per_page 50 :include_totals true}})] ; todo no kws
                (assert (= status 200))
                (let [users (:users body)] ; json
                  (when (seq users)
                    {:value (mapv #(merge (->Auth0Entity o) (keywordize-keys %)) users)
                     :next-k (inc page)}))))]
      (->> (iteration step :somef seq :vf :value :kf :next-k :initk 0)
        (sequence cat))))

  (user [o user-id]
    (let [{::http/keys [status body]} (http/caching-get! !okhttp (format "https://%s/api/v2/users/%s" (::domain config) user-id)
                                        {:headers {"Authorization" (format "Bearer %s" (.-token o))}})]
      (assert (= status 200))
      (merge (->Auth0Entity o) (auto-props body))))

  (export-users [o ?opts]
    (let [{:keys [connection_id format fields limit]
           :or {format "json" limit 100000}} ?opts
          url (format "https://%s/api/v2/jobs/users-exports" (::domain config))
          headers {"Authorization" (format "Bearer %s" (.-token o))
                   "Content-Type" "application/json"}
          body (cond-> {}
                 connection_id (assoc :connection_id connection_id)
                 format (assoc :format format)
                 fields (assoc :fields fields)
                 limit (assoc :limit limit))
          {:keys [status body]} (clj-http.client/post url {:headers headers :body (clojure.data.json/write-str body) :as :json})]
      (assert (= status 200))
      body))

  (check-job-status [o job-id]
    (let [url (format "https://%s/api/v2/jobs/%s" (::domain config) job-id)
          headers {"Authorization" (format "Bearer %s" (.-token o))}
          {::http/keys [status body]} (http/caching-get! !okhttp url {:headers headers})]
      (assert (= status 200))
      (keywordize-keys body)))

  (download-export [o url]
    (let [{:keys [status body]} (clj-http.client/get url {:as :stream})]
      (assert (= status 200))
      (with-open [gzip (java.util.zip.GZIPInputStream. body)]
        (slurp gzip))))

  (search-users [o qstr]
    (letfn [(step [page]
              (let [{::http/keys [status body]} (http/caching-get! !okhttp (format "https://%s/api/v2/users" (::domain config))
                                                  {:headers {"Authorization" (format "Bearer %s" (.-token o))}
                                                   :query-params (cond-> {:page page :per_page 50 :include_totals true}
                                                                   qstr (assoc :q qstr :search_engine "v3"))})]
                (assert (= status 200))
                (let [users (:users body)]
                  (when (seq users)
                    {:value (mapv #(merge (->Auth0Entity o) (keywordize-keys %)) users)
                     :next-k (inc page)}))))]
      (->> (iteration step :somef seq :vf :value :kf :next-k :initk 0)
        (sequence cat)))))

(defn get-users-export ; 3 seconds
  "Initiates, polls, and retrieves a user export from Auth0, returning parsed JSON or CSV data."
  [!auth0 & [opts]]
  (let [opts (merge {:format "json"} opts)
        {:keys [id]} (export-users !auth0 opts)]
    (loop []
      (Thread/sleep 2000)
      (let [status (check-job-status !auth0 id)]
        (if (= (:status status) "completed")
          (let [export-url (:location status)
                content (download-export !auth0 export-url)]
            (condp = (:format opts)
              "json" (->> (clojure.string/split-lines content) (mapv #(cheshire.core/parse-string % true))) ; parse NDJSON
              ;"csv" (with-open [reader (io/reader (char-array content))] (doall (csv/read-csv reader)))
              content))
          (recur))))))

(defn max-updated-at [users] (when (seq users) (apply max (map :updated_at users))))

; https://auth0.com/docs/secure/tokens/access-tokens/management-api-access-tokens/get-management-api-access-tokens-for-testing
; https://manage.auth0.com/dashboard/us/hyperfiddle/apis/671799c13e2806273af301a9/explorer
(def config (comptime-resource "dustingetz/nav_auth0.edn"))

(def !http-cache (clojure.java.io/file "../scratch/src/dustingetz/okhttp-cache"))
(defn auth0 [] (touch (Auth0Client. config (http/caching-client !http-cache) nil)))
(comment

  (def !auth0 (time (auth0)))
  (def xs (time (count (users !auth0))))
  (def xs (time (get-users-export !auth0)))
  (count xs)
  (count (search-users !auth0 (format "updated_at:{%s TO *}" "2025-07-01")))
  )



(extend-protocol hfql/Suggestable
  Auth0Client (-suggest [_] (hfql/pull-spec [users]))
  Auth0Entity (-suggest [_] (hfql/pull-spec [:email
                                             :name
                                             :last_login
                                             :nickname
                                             :email_verified
                                             :updated_at
                                             :picture
                                             :last_ip
                                             :user_id
                                             :logins_count
                                             :identities
                                             :created_at])))