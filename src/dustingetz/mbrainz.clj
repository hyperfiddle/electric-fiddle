(ns dustingetz.mbrainz
  (:require [datomic.api :as d] ; com.datomic/peer {:mvn/version "1.0.7075"}
            #_[hyperfiddle.electric3 :as e] ; not allowed in .clj
            ))

(def mbrainz-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")
(def test-conn (delay (d/connect mbrainz-uri)))
(def test-db (delay (d/db @test-conn)))
(def lennon 527765581346058) ; datomic pro

(comment
  (d/touch (d/entity @test-db 100))
  := #:db{:id 100,
          :ident :release/name,
          :valueType :db.type/string,
          :cardinality :db.cardinality/one,
          :index true,
          :fulltext true,
          :doc "The name of the release"})
