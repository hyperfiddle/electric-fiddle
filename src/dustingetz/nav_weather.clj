(ns dustingetz.nav-weather
  "Weather browser via Open-Meteo API (no API key required)."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]]))

;; -- Records --

(defrecord WeatherCurrent [latitude longitude temperature-c feels-like-c
                           humidity-pct wind-speed-kmh wind-direction-deg
                           precipitation-mm weather-code is-day])

(defrecord WeatherHourly [time temperature-c humidity-pct precipitation-mm
                          wind-speed-kmh cloud-cover-pct])

;; -- Data functions --

(defn current-weather
  "Get current weather for lat/lon. Defaults to San Francisco."
  ([] (current-weather 37.7749 -122.4194))
  ([lat lon]
   (when-let [data (http/fetch-json
                     (str "https://api.open-meteo.com/v1/forecast"
                       "?latitude=" lat "&longitude=" lon
                       "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                       "precipitation,weather_code,wind_speed_10m,wind_direction_10m,is_day"))]
     (let [c (:current data)]
       (->WeatherCurrent
         lat lon
         (:temperature_2m c)
         (:apparent_temperature c)
         (:relative_humidity_2m c)
         (:wind_speed_10m c)
         (:wind_direction_10m c)
         (:precipitation c)
         (:weather_code c)
         (:is_day c))))))

(defn hourly-forecast
  "Get 24h hourly forecast for lat/lon."
  ([] (hourly-forecast 37.7749 -122.4194))
  ([lat lon]
   (when-let [data (http/fetch-json
                     (str "https://api.open-meteo.com/v1/forecast"
                       "?latitude=" lat "&longitude=" lon
                       "&hourly=temperature_2m,relative_humidity_2m,"
                       "precipitation,wind_speed_10m,cloud_cover"
                       "&forecast_days=1"))]
     (let [h (:hourly data)
           times (:time h)
           temps (:temperature_2m h)
           humids (:relative_humidity_2m h)
           precips (:precipitation h)
           winds (:wind_speed_10m h)
           clouds (:cloud_cover h)]
       (mapv (fn [i]
               (->WeatherHourly
                 (nth times i) (nth temps i) (nth humids i)
                 (nth precips i) (nth winds i) (nth clouds i)))
         (range (count times)))))))

;; -- Named accessors --

(defn weather-summary [w]
  (str (:temperature-c w) "°C, "
    (:humidity-pct w) "% humidity, "
    (:wind-speed-kmh w) " km/h wind"))

(defn hourly-time [h] (:time h))

;; -- Protocol extensions --

(extend-type WeatherCurrent
  Identifiable (-identify [w] `(current-weather ~(:latitude w) ~(:longitude w)))
  Suggestable (-suggest [_] (hfql [:latitude :longitude :temperature-c :feels-like-c
                                   :humidity-pct :wind-speed-kmh :wind-direction-deg
                                   :precipitation-mm :weather-code :is-day])))

(extend-type WeatherHourly
  Identifiable (-identify [_] `(hourly-forecast))
  Suggestable (-suggest [_] (hfql [:time :temperature-c :humidity-pct :precipitation-mm
                                   :wind-speed-kmh :cloud-cover-pct])))

;; -- Sitemap --

(def sitemap
  {`current-weather
   (hfql {(current-weather) [weather-summary :temperature-c :feels-like-c
                             :humidity-pct :wind-speed-kmh :wind-direction-deg
                             :precipitation-mm :weather-code]})

   `hourly-forecast
   (hfql {(hourly-forecast) {* [hourly-time :temperature-c :humidity-pct
                                :precipitation-mm :wind-speed-kmh :cloud-cover-pct]}})})

;; -- Resolvers --

(defmethod -hfql-resolve `current-weather
  [form]
  (let [[_ lat lon] form]
    (if lat (current-weather lat lon) (current-weather))))

(defmethod -hfql-resolve `hourly-forecast
  [form]
  (let [[_ lat lon] form]
    (if lat (hourly-forecast lat lon) (hourly-forecast))))
