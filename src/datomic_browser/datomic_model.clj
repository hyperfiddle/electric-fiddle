(ns datomic-browser.datomic-model
  (:require [datomic-browser.contrib :refer [unqualify seq-consumer]]
            [datomic.api :as d] ; cloud would require a facade
            [missionary.core :as m]))

(comment
  (require '[dustingetz.mbrainz :refer [test-db test-conn]]))

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
  (->> (attributes-stream @test-db [:db/ident]) (m/reduce conj []) m/?) := [...])

(defn entity-history-datoms [db & [?e ?a]]
  (m/ap
    (let [history (d/history db)
          ; (sequence #_(comp (xf-filter-before-date before)))
          >fwd-xs (seq-consumer (d/datoms history :eavt ?e ?a))
          >rev-xs (seq-consumer (d/datoms history :vaet ?e ?a))]
      (m/amb= (m/?> >fwd-xs) (m/?> >rev-xs)))))
