(ns dustingetz.nav-bgp
  "BGP routing table prefix browser via RIPE RIPEstat. The internet's actual routing table."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]]))

;; -- Records --

(defrecord BgpPrefix [prefix timelines])
(defrecord AsInfo [asn holder country])
(defrecord BgpPeer [asn ip-address prefix-count])

;; -- Data functions --

(defn as-prefixes
  "Announced prefixes for an AS number. Default: AS13335 (Cloudflare)."
  ([] (as-prefixes "AS13335"))
  ([asn]
   (when-let [data (http/fetch-json
                     (str "https://stat.ripe.net/data/announced-prefixes/data.json?resource=" asn)
                     {:timeout-seconds 20})]
     (let [prefixes (get-in data [:data :prefixes])]
       (->> prefixes
         (take 100)
         (mapv (fn [p]
                 (->BgpPrefix
                   (:prefix p)
                   (str (count (:timelines p)) " timeline(s)")))))))))

(defn as-info
  "Information about an AS number."
  ([] (as-info "AS13335"))
  ([asn]
   (when-let [data (http/fetch-json
                     (str "https://stat.ripe.net/data/as-overview/data.json?resource=" asn)
                     {:timeout-seconds 20})]
     (let [d (:data data)]
       (->AsInfo
         (get d :resource asn)
         (get d :holder "")
         (get-in d [:holder_info :country] ""))))))

(defn bgp-peers
  "BGP peers for an AS number."
  ([] (bgp-peers "AS13335"))
  ([asn]
   (when-let [data (http/fetch-json
                     (str "https://stat.ripe.net/data/asn-neighbours/data.json?resource=" asn)
                     {:timeout-seconds 20})]
     (let [neighbours (get-in data [:data :neighbours])]
       (->> neighbours
         (take 50)
         (mapv (fn [n]
                 (->BgpPeer
                   (get n :asn "")
                   ""
                   (get n :power 0)))))))))

;; -- Named accessors --

(defn prefix-display [p] (:prefix p))
(defn as-display [a] (str (:asn a) " - " (:holder a)))
(defn peer-display [p] (str "AS" (:asn p)))

;; -- Protocol extensions --

(extend-type BgpPrefix
  Identifiable (-identify [_] `(as-prefixes))
  Suggestable (-suggest [_] (hfql [:prefix :timelines])))

(extend-type AsInfo
  Identifiable (-identify [a] `(as-info ~(:asn a)))
  Suggestable (-suggest [_] (hfql [:asn :holder :country])))

(extend-type BgpPeer
  Identifiable (-identify [p] `(as-info ~(:asn p)))
  Suggestable (-suggest [_] (hfql [:asn :ip-address :prefix-count])))

;; -- Sitemap --

(def sitemap
  {`as-prefixes
   (hfql {(as-prefixes) {* [prefix-display :timelines]}})

   `as-info
   (hfql {(as-info) [as-display :asn :holder :country]})

   `bgp-peers
   (hfql {(bgp-peers) {* ^{::hfql/select `as-info}
                          [^{::hfql/link `(as-info ~'%)} peer-display
                           :asn :prefix-count]}})})

;; -- Resolvers --

(defmethod -hfql-resolve `as-prefixes
  [form]
  (let [[_ asn] form]
    (if asn (as-prefixes asn) (as-prefixes))))

(defmethod -hfql-resolve `as-info
  [form]
  (let [[_ asn] form]
    (if asn (as-info asn) (as-info))))

(defmethod -hfql-resolve `bgp-peers
  [form]
  (let [[_ asn] form]
    (if asn (bgp-peers asn) (bgp-peers))))
