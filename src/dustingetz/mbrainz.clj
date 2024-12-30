(ns dustingetz.mbrainz
  (:require [datomic.api :as d] ; com.datomic/peer {:mvn/version "1.0.7075"}
            #_[hyperfiddle.electric3 :as e] ; not allowed in .clj
            ))

(def mbrainz-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")

(comment
  (def conn (d/connect mbrainz-uri))
  (def db (d/db conn))
  (d/touch (d/entity db 100))
  := #:db{:id 100,
          :ident :release/name,
          :valueType :db.type/string,
          :cardinality :db.cardinality/one,
          :index true,
          :fulltext true,
          :doc "The name of the release"})
