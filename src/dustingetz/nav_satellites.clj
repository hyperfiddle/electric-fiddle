(ns dustingetz.nav-satellites
  "CelesTrak satellite TLE browser. What's flying over you right now."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]]))

;; -- Records --

(defrecord Satellite [norad-id name epoch inclination raan eccentricity
                      arg-perigee mean-anomaly mean-motion rev-number
                      classification launch-year object-type])

;; -- Helpers --

(defn- gp->satellite [gp]
  (->Satellite
    (get gp :NORAD_CAT_ID "")
    (str/trim (get gp :OBJECT_NAME ""))
    (get gp :EPOCH "")
    (get gp :INCLINATION "")
    (get gp :RA_OF_ASC_NODE "")
    (get gp :ECCENTRICITY "")
    (get gp :ARG_OF_PERICENTER "")
    (get gp :MEAN_ANOMALY "")
    (get gp :MEAN_MOTION "")
    (get gp :REV_AT_EPOCH "")
    (get gp :CLASSIFICATION_TYPE "")
    (get gp :LAUNCH_DATE "")
    (get gp :OBJECT_TYPE "")))

;; -- Data functions --

(defn active-satellites
  "First 100 active satellites from CelesTrak GP data."
  []
  (when-let [data (http/fetch-json "https://celestrak.org/NORAD/elements/gp.php?GROUP=last-30-days&FORMAT=json"
                                   {:timeout-seconds 30})]
    (->> data (take 100) (mapv gp->satellite))))

(defn starlink-sats
  "Starlink constellation satellites."
  []
  (when-let [data (http/fetch-json "https://celestrak.org/NORAD/elements/gp.php?GROUP=starlink&FORMAT=json"
                                   {:timeout-seconds 30})]
    (->> data (take 100) (mapv gp->satellite))))

(defn iss-tle
  "International Space Station TLE."
  []
  (when-let [data (http/fetch-json "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=json"
                                   {:timeout-seconds 30})]
    (when (seq data) (gp->satellite (first data)))))

(defn find-satellite
  "Single satellite by NORAD catalog ID from CelesTrak GP data."
  [norad-id]
  (when-let [data (http/fetch-json (str "https://celestrak.org/NORAD/elements/gp.php?CATNR=" norad-id "&FORMAT=json")
                                   {:timeout-seconds 30})]
    (when (seq data) (gp->satellite (first data)))))

;; -- Named accessors --

(defn sat-display [s] (str (:name s) " [" (:norad-id s) "]"))
(defn sat-id [s] (:norad-id s))

;; -- Protocol extensions --

(extend-type Satellite
  Identifiable (-identify [s] `(find-satellite ~(:norad-id s)))
  Suggestable (-suggest [_] (hfql [:norad-id :name :epoch :inclination :raan
                                   :eccentricity :arg-perigee :mean-anomaly
                                   :mean-motion :rev-number :classification
                                   :launch-year :object-type])))

;; -- Sitemap --

(def sitemap
  {`active-satellites
   (hfql {(active-satellites) {* ^{::hfql/select `find-satellite}
                                 [^{::hfql/link `(find-satellite ~'%)} sat-display
                                  :inclination :mean-motion :object-type]}})

   `starlink-sats
   (hfql {(starlink-sats) {* ^{::hfql/select `find-satellite}
                              [^{::hfql/link `(find-satellite ~'%)} sat-display
                               :epoch :inclination :mean-motion]}})

   `iss-tle
   (hfql {(iss-tle) [:norad-id :name :epoch :inclination :raan :eccentricity
                     :mean-motion :rev-number]})

   `find-satellite
   (hfql [:norad-id :name :epoch :inclination :raan :eccentricity
          :arg-perigee :mean-anomaly :mean-motion :rev-number
          :classification :launch-year :object-type])})

;; -- Resolvers --

(defmethod -hfql-resolve `active-satellites [_] (active-satellites))
(defmethod -hfql-resolve `starlink-sats [_] (starlink-sats))
(defmethod -hfql-resolve `iss-tle [_] (iss-tle))
(defmethod -hfql-resolve `find-satellite [[_ id]] (find-satellite id))
