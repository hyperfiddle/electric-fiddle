(ns dustingetz.datafy-aws
  "hyperdata wrapper for the lower level cognitect.aws"
  (:require [clojure.core.protocols :refer [nav]]
            [clojure.datafy :refer [datafy]]
            [clojure.spec.alpha :as s]
            [cognitect.aws.client.api :as aws]
            [contrib.assert :refer [check]]
            [contrib.data :refer [qualify]]
            [hyperfiddle.rcf :refer [tests]]))

; Hypermedia design notes
; we do NOT want an infinite trail of breadcrumbs as we navigate
; because that means when we refresh the page we have to traverse that long trail
; instead we want a set of top-level, routable APIs
; which seems in tension with using the nav protocol to implement hyperlinks
; in fact, what is nav even for if it is not routable?
; the whole point of extend-via-metadata is that it isn't routable!
;
; That implies that a generalized data browser is at odds with a routable web app
; related: tension between indexed collections and sequential collections

(comment
  (def s3 (aws {:api :s3 :region "us-east-1"}))
  (datafy s3)
  (as-> s3 x
    (datafy x)
    (nav x :ops (:ops x))
    (datafy x)
    (first (filter #(= "ListBuckets" (:name %)) x))
    (datafy x))
  )

(def ... '...) ; hyperlink (guard heavy IO and/or large/unweildy collections)

(declare
  list-object-versions
  list-buckets
  list-objects
  buckets
  bucket
  ops
  )

(defn aws [config]
  (let [aws (aws/client config)
        m (datafy aws)] ; super
    (with-meta aws
      {`clojure.core.protocols/datafy
       (fn [x] ; override
         (with-meta
           (merge
             (-> (select-keys m [:service :api :region :endpoint])
               #_(update-keys #(qualify %)))
             {:ops ...})
           {`clojure.core.protocols/nav
            (fn [m' k v]
              (case k
                ; present indexed map in table/row form for display.
                ; should datagrid support indexed structures?
                :ops (ops aws m)
                v))}))})))

(defn ops [aws service-map]
  (->> (sort-by :name (vals (:ops service-map)))
    (mapv (fn [op]
            (with-meta op
              {`clojure.core.protocols/datafy
               (fn [x]
                 (with-meta
                   (merge
                     (select-keys op [:name :documentation])
                     {:invoke-op! '...})
                   {`clojure.core.protocols/nav
                    (fn [m' k v]
                      (case k
                        :invoke-op! (list-buckets aws)))}))})))))

(defn list-buckets [s3]
  (with-meta
    (aws/invoke s3 {:op :ListBuckets})
    {`clojure.core.protocols/datafy
     (fn [x]
       (with-meta
         (-> x (update :Buckets (constantly '...)))
         {`clojure.core.protocols/nav
          (fn [m' k v]
            (case k
              :Buckets (buckets s3 x)))}))}))

(defn- buckets [s3 x]
  (with-meta (:Buckets x) ; original
    {`clojure.core.protocols/datafy
     (fn [xs]
       (with-meta (mapv (partial bucket s3) xs) ; parsed
         {`clojure.core.protocols/nav
          (fn [m k v]
            v)}))}))

(defn- bucket [s3 x]
  (with-meta
    (-> x #_(update :Buckets (constantly '...))) ; remove large collections
    {`clojure.core.protocols/datafy
     (fn [x]
       (with-meta
         (merge
           (select-keys x [:Name :CreationDate]) ; parse foreign object
           {:ListObjectVersions '...}) ; advertise link
         {`clojure.core.protocols/nav ; implement link
          (fn [m k v]
            (case k
              :ListObjectVersions (list-object-versions s3 (:Name x))))}))}))

(defn list-objects [s3 bucket-name]
  (with-meta
    (aws/invoke s3 {:op :ListObjects :request {:Bucket (check bucket-name)}})
    {`clojure.core.protocols/datafy
     (fn [x]
       (with-meta
         (-> x #_(update :Buckets (constantly '...)))
         {`clojure.core.protocols/nav
          (fn [m' k v]
            v
            #_(case k
              :Buckets (buckets s3 x)))}))}))

(comment (list-objects s3 "www.hyperfiddle.net"))


(defn list-object-versions [aws bucket-name]
  (with-meta
    (aws/invoke aws {:op :ListObjectVersions :Bucket bucket-name})
    {`clojure.core.protocols/datafy
     (fn [x]
       (with-meta
         x
         {`clojure.core.protocols/nav
          (fn [m' k v]
            #_(case k :Buckets (buckets aws x)))}))}))

(comment
  (def s3x (aws/client {:api :s3 :region "us-east-1"}))
  (type s3x)
  (vec (datafy s3x))
  #_(aws/validate-requests s3 true) ; broken
  (def x (aws/invoke s3x {:op :ListBuckets}))
  (meta x)
  (as-> x x
    (nav x :Buckets (:Buckets x)))
  )
