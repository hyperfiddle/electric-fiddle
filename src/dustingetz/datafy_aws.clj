(ns dustingetz.datafy-aws
  "hyperdata wrapper for the lower level cognitect.aws"
  (:require [clojure.core.protocols :refer [nav]]
            [clojure.datafy :refer [datafy]]
            [clojure.spec.alpha :as s]
            [cognitect.aws.client.api :as aws]
            [contrib.assert :refer [check]]
            [contrib.data :refer [qualify]]
            [hyperfiddle.rcf :refer [tests]]))

(def ... '...) ; hyperlink (guard heavy IO and/or large/unweildy collections)

(defn ops [m]
  (vec (sort-by :name (vals (:ops m)))))

(defn aws [config]
  (let [x (aws/client config)
        m (datafy x)] ; super
    (with-meta x
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
                :ops (ops m)))}))})))

(comment
  (def s3 (aws {:api :s3 :region "us-east-1"}))
  (datafy s3)
  (as-> s3 x
    (datafy x)
    (nav x ::ops (::ops x)))
  )

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

#_(extend-protocol ccp/Datafiable
  .
  (datafy [x]
    )
  )