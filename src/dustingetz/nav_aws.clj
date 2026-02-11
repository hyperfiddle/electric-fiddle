(ns dustingetz.nav-aws
  ;#?(:clj (:import (cognitect.aws.client.impl Client))) - really struggling to import this deftype as a class
  (:require
   [cognitect.aws.client.api :as aws]
   [contrib.assert :refer [check]]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

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
  (list-object-versions [o] (aws/invoke !s3 {:op :ListObjectVersions, :request {:Bucket (check bucket-name)}})))

(defrecord S3 [!s3]
  IAWS
  (aws-ops [o] (aws/ops !s3))
  IS3
  (list-buckets [o] (->> (aws/invoke !s3 {:op :ListBuckets}) :Buckets (map #(->S3Bucket !s3 (check (:Name %)))))))

(defn aws-s3-us-east [] (S3. (aws/client {:api :s3 :region "us-east-1"})))

(extend-type S3
  hfqlp/Suggestable (-suggest [_] (hfql [list-buckets :service :api :region :endpoint type])))

(extend-type S3Bucket
  ;; Listing buckets list buckets in all regions.
  ;; But buckets are region-sensitive (can't list bucket objects in wrong region) and constructing a bucket object requires DI of an S3 client.
  ;; Therefore a bucket's identity must contain at least the bucket name and its region.
  ;; Calling the Bucket constructor from a symbolically identified bucket requires an S3 client (DI). Not clear how to do this.
  ;; TODO
  ;; hfqlp/Identifiable (-identify [bucket] `(s3-bucket (:bucket-name bucket) _region_)
  hfqlp/Suggestable (-suggest [_] (hfql [type pr-str
                                         list-object-versions ; FIXME blows up in userland if some bucket is not in "us-east-1"
                                         ])))
