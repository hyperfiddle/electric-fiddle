(ns datomic-browser.datomic-model
  (:require [datomic.api :as d] ; cloud would require a facade, which we have but it's complicated
            [datomic-browser.contrib :refer [unqualify]]
            [missionary.core :as m]
            [hyperfiddle.rcf :refer [tests]]))

(tests (require '[dustingetz.mbrainz :refer [test-db test-conn lennon]]))

(defn ident! "resolve Datomic entity-id to ident for display"
  [db ?e] (m/via m/blk (or (some->> ?e (d/entity db) :db/ident) ?e)))

(tests (m/? (ident! @test-db 1)) := :db/add)

(defn identify "infer canonical identity from Datomic entity, in precedence order."
  [tree] (first (remove nil? ((juxt :db/ident :db/id) tree))))

(tests
  (identify (d/entity @test-db 1)) := :db/add
  (identify (d/entity @test-db :db/add)) := :db/add
  (identify (d/entity @test-db 1000)) := 1000
  (identify (d/entity @test-db nil)) := nil)

(defn easy-attr [db ?a]
  (when ?a
    (let [!e (d/entity db ?a)]
      [(unqualify (:db/valueType !e))
       (unqualify (:db/cardinality !e))
       (unqualify (:db/unique !e))
       (if (:db/isComponent !e) :component)])))

(tests
  (easy-attr @test-db :db/ident) := [:keyword :one :identity nil]
  (easy-attr @test-db :artist/name) := [:string :one nil nil])

(defn summarize-attr [db ?a]
  (when ?a
    (remove nil? (easy-attr db ?a))))

(tests
  (summarize-attr @test-db :db/ident) := [:keyword :one :identity]
  (summarize-attr @test-db :artist/name) := [:string :one]
  (summarize-attr @test-db :db/id) := [])

(defn is-attr? [db ?k] (when ?k (some? (:db/valueType (d/entity db ?k)))))

(tests
  (is-attr? @test-db :db/ident) := true
  (is-attr? @test-db :db/id) := false
  (is-attr? @test-db :artist/name) := true
  (is-attr? @test-db :artist/name1) := false)

(defn seq-consumer [xs] ; xs is iterable
  (m/ap
    (loop [xs xs]
      (if (m/? (m/via m/blk (seq xs)))
        (m/amb (m/? (m/via m/blk (first xs))) (recur (rest xs)))
        (m/amb)))))

(defn attributes-stream [db pull]
  (->> (d/qseq {:args [db pull]
                :query '[:find (pull ?e pattern)
                         :in $ pattern
                         :where [?e :db/valueType _]]})
    seq-consumer (m/eduction (map first))))

(tests
  (->> (attributes-stream @test-db [:db/ident]) (m/reduce conj []) m/?)
  (count *1) := 83
  (take 3 *2) := [#:db{:ident :db/system-tx} #:db{:ident :db.sys/partiallyIndexed} #:db{:ident :db.sys/reId}])

(defn entity-history-datoms-stream [db & [?e ?a]]
  (m/ap
    (let [history (d/history db)
          ; (sequence #_(comp (xf-filter-before-date before)))
          >fwd-xs (seq-consumer (d/datoms history :eavt ?e ?a))
          >rev-xs (seq-consumer (d/datoms history :vaet ?e ?a))]
      (m/amb= (m/?> >fwd-xs) (m/?> >rev-xs))))) ; race them

(tests
  (require '[clojure.datafy :refer [datafy]])
  (def xs (->> (entity-history-datoms-stream @test-db lennon) (m/reduce conj []) m/?
            (sort-by :e) ; stabilize test (race)
            ))
  (count xs) := 173
  (->> (take 3 xs) (map datafy))
  := [[17592186061822 81 527765581346058 13194139557428 true]
      [17592186061893 81 527765581346058 13194139557428 true]
      [17592186062377 81 527765581346058 13194139557428 true]])