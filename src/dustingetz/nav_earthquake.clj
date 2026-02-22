(ns dustingetz.nav-earthquake
  "USGS real-time earthquake feed browser."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]])
  (:import
   [java.time Instant LocalDateTime ZoneId]
   [java.time.format DateTimeFormatter]))

;; -- Records --

(defrecord Earthquake [id magnitude place time depth latitude longitude
                       tsunami alert felt-reports status])

;; -- Helpers --

(def ^:private dt-fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss z"))

(defn- epoch-ms->str [ms]
  (try
    (let [inst (Instant/ofEpochMilli (long ms))
          ldt (LocalDateTime/ofInstant inst (ZoneId/of "UTC"))]
      (.format ldt dt-fmt))
    (catch Exception _ (str ms))))

(defn- parse-feature [f]
  (let [props (:properties f)
        geo (:geometry f)
        coords (:coordinates geo)]
    (->Earthquake
      (:id f)
      (:mag props)
      (:place props)
      (epoch-ms->str (:time props))
      (when (>= (count coords) 3) (nth coords 2))
      (when (>= (count coords) 2) (nth coords 1))
      (when (seq coords) (nth coords 0))
      (:tsunami props)
      (:alert props)
      (:felt props)
      (:status props))))

;; -- Data functions --

(defn recent-quakes
  "All earthquakes in the past hour."
  []
  (when-let [data (http/fetch-json "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson")]
    (mapv parse-feature (:features data))))

(defn significant-quakes
  "Significant earthquakes in the past day."
  []
  (when-let [data (http/fetch-json "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_day.geojson")]
    (mapv parse-feature (:features data))))

(defn quakes-past-day
  "All M2.5+ earthquakes in the past 24 hours."
  []
  (when-let [data (http/fetch-json "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson")]
    (mapv parse-feature (:features data))))

(defn find-quake
  "USGS earthquake detail by event ID from the GeoJSON detail feed."
  [id]
  (when-let [data (http/fetch-json (str "https://earthquake.usgs.gov/earthquakes/feed/v1.0/detail/" id ".geojson"))]
    (parse-feature data)))

;; -- Named accessors --

(defn quake-display [q]
  (str "M" (:magnitude q) " " (:place q)))

(defn quake-id [q] (:id q))

;; -- Protocol extensions --

(extend-type Earthquake
  Identifiable (-identify [q] `(find-quake ~(:id q)))
  Suggestable (-suggest [_] (hfql [:id :magnitude :place :time :depth
                                   :latitude :longitude :tsunami :alert
                                   :felt-reports :status])))

;; -- Sitemap --

(def sitemap
  {`recent-quakes
   (hfql {(recent-quakes) {* ^{::hfql/select `find-quake}
                              [^{::hfql/link `(find-quake ~'%)} quake-display
                               :magnitude :time :depth]}})

   `significant-quakes
   (hfql {(significant-quakes) {* ^{::hfql/select `find-quake}
                                  [^{::hfql/link `(find-quake ~'%)} quake-display
                                   :magnitude :time :depth :felt-reports]}})

   `quakes-past-day
   (hfql {(quakes-past-day) {* ^{::hfql/select `find-quake}
                               [^{::hfql/link `(find-quake ~'%)} quake-display
                                :magnitude :time :depth]}})

   `find-quake
   (hfql [:id :magnitude :place :time :depth :latitude :longitude
          :tsunami :alert :felt-reports :status])})

;; -- Resolvers --

(defmethod -hfql-resolve `recent-quakes [_] (recent-quakes))
(defmethod -hfql-resolve `significant-quakes [_] (significant-quakes))
(defmethod -hfql-resolve `quakes-past-day [_] (quakes-past-day))
(defmethod -hfql-resolve `find-quake [[_ id]] (find-quake id))
