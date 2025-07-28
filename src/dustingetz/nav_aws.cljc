(ns dustingetz.nav-aws
  ;#?(:clj (:import (cognitect.aws.client.impl Client))) - really struggling to import this deftype as a class
  (:require
    #?(:clj [cognitect.aws.client.api :as aws])
    [contrib.assert :refer [check]]
    [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]))

#?(:clj
   (do

     (defprotocol IAWS
       (aws-ops [o]))

     (defprotocol IS3
       (list-buckets [o]))

     (defprotocol IS3Bucket
       (list-objects [o])
       (list-object-versions [o]))

     (defrecord S3Bucket [!s3 bucket-name]
       IS3Bucket
       (list-objects [o] (aws/invoke !s3 {:op :ListObjects :request {:Bucket (check bucket-name)}}))
       (list-object-versions [o] (aws/invoke !s3 {:op :ListObjectVersions :Bucket (check bucket-name)})))

     (defrecord S3 [!s3]
       IAWS
       (aws-ops [o] (vals (aws/ops !s3)))
       IS3
       (list-buckets [o] (->> (aws/invoke !s3 {:op :ListBuckets}) :Buckets (map #(->S3Bucket !s3 (check (:Name %)))))))

     (defn aws-s3-us-east [] (S3. (aws/client {:api :s3 :region "us-east-1"})))

     (def sitemap (hfql/sitemap {aws-s3-us-east [list-buckets aws-ops]}))

     (extend-protocol hfql/Suggestable
       S3 (-suggest [_] (hfql/pull-spec [list-buckets :service :api :region :endpoint type]))
       S3Bucket (-suggest [_] (hfql/pull-spec [type pr-str list-object-versions]))
       )

     ))