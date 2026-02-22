(ns dustingetz.nav-util.http
  "Shared HTTP client utilities for nav files.
   Provides fetch-json and fetch-text with configurable timeouts and headers."
  (:require [clojure.data.json :as json])
  (:import
   [java.net URI]
   [java.net.http HttpClient HttpClient$Redirect HttpRequest HttpResponse$BodyHandlers]
   [java.time Duration]))

(defonce ^:private default-client
  (-> (HttpClient/newBuilder)
    (.connectTimeout (Duration/ofSeconds 10))
    (.followRedirects HttpClient$Redirect/NORMAL)
    (.build)))

(defn fetch-text
  "GET a URL and return the response body as a string, or nil on failure.
   opts map (all optional):
     :timeout-seconds  - request timeout (default 15)
     :headers          - map of header name->value
     :client           - custom HttpClient (default: shared client)"
  ([url] (fetch-text url nil))
  ([url opts]
   (try
     (let [timeout (or (:timeout-seconds opts) 15)
           client  (or (:client opts) default-client)
           builder (doto (HttpRequest/newBuilder)
                     (.uri (URI/create url))
                     (.timeout (Duration/ofSeconds timeout))
                     (.GET))]
       (doseq [[k v] (:headers opts)]
         (.header builder (str k) (str v)))
       (let [req  (.build builder)
             resp (.send client req (HttpResponse$BodyHandlers/ofString))]
         (when (= 200 (.statusCode resp))
           (.body resp))))
     (catch Exception _ nil))))

(defn fetch-json
  "GET a URL and return parsed JSON (keyword keys), or nil on failure.
   Accepts same opts map as fetch-text."
  ([url] (fetch-json url nil))
  ([url opts]
   (when-let [body (fetch-text url opts)]
     (try
       (json/read-str body :key-fn keyword)
       (catch Exception _ nil)))))
