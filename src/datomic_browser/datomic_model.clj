(ns datomic-browser.datomic-model
  (:require [contrib.data :refer [unqualify]]
            [datomic.api :as d] ; cloud requires facade
            [missionary.core :as m]))

(comment
  (require '[dustingetz.mbrainz :refer [test-db test-conn]]))

(defn seq-consumer [xs] ; xs is iterable
  (m/ap
    (loop [xs xs]
      (if (m/? (m/via m/blk (seq xs)))
        (m/amb (m/? (m/via m/blk (first xs))) (recur (rest xs)))
        (m/amb)))))

(defn ident! "resolve Datomic entity-id to ident for display"
  [db ?e] (m/via m/blk (or (some->> ?e (d/entity db) :db/ident) ?e)))

(defn identify "infer canonical identity from Datomic entity, in precedence order."
  [tree] (first (remove nil? ((juxt :db/ident :db/id) tree))))

(defn easy-attr [db ?a]
  (when ?a
    (let [!e (d/entity db ?a)]
      [(unqualify (:db/valueType !e))
       (unqualify (:db/cardinality !e))
       (unqualify (:db/unique !e))
       (if (:db/isComponent !e) :component)])))

(comment (easy-attr @test-db :db/ident))

(defn summarize-attr [db ?a]
  (when ?a
    (remove nil? (easy-attr db ?a))))

(comment (summarize-attr @test-db :db/ident))

(defn is-attr? [db ?k] (when ?k (some? (:db/valueType (d/entity db ?k)))))

(defn attributes-stream [db pull]
  (->> (d/qseq {:args [db pull]
                :query '[:find (pull ?e pattern)
                         :in $ pattern
                         :where [?e :db/valueType _]]})
    seq-consumer (m/eduction (map first))))

(comment
  (def test-conn (d/connect dustingetz.mbrainz/mbrainz-uri))
  (def test-db (d/db test-conn))
  (->> (attributes-stream test-db [:db/ident]) (m/reduce conj []) m/?) := [...])

(defn entity-history-datoms [db & [?e ?a]]
  (m/ap
    (let [history (d/history db)
          ; (sequence #_(comp (xf-filter-before-date before)))
          >fwd-xs (seq-consumer (d/datoms history :eavt ?e ?a))
          >rev-xs (seq-consumer (d/datoms history :vaet ?e ?a))]
      (m/amb= (m/?> >fwd-xs) (m/?> >rev-xs)))))

(defn flatten-nested ; claude generated this
  ([data] (flatten-nested data []))
  ([data path]
   (cond
     (map? data)
     (mapcat (fn [[k v]]
               (cond
                 (map? v)
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 (and (sequential? v) (map? (first v)))
                 #_[{:path path :name k :value '...}] ; elide collections of records
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 ; render simple collections inline
                 (and (sequential? v) (not (map? (first v))))
                 [{:path path :name k :value v}]

                 #_#_(nil? v) [{:path path :name k}]
                 () [{:path path :name k :value v}]))
       data)

     ; render simple collections as indexed maps
     (sequential? data)
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:path path :name i}
                   (flatten-nested v (conj path i)))

                 ()
                 [{:path path :name i :value v}]))
       (range) data)

     ; what else?
     () [{:path path :value data}])))

(comment
  (def test-data
    '{:response
      {:Owner
       {:DisplayName string
        :ID string}
       :Grants
       {:seq-of
        {:Grantee
         {:DisplayName string
          :EmailAddress string
          :ID string
          :Type [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]
          :URI string}
         :Permission [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]}}}})
  (flatten-nested test-data)
  )