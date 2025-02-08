(ns dustingetz.mbrainz
  (:require [datomic.api :as d] ; com.datomic/peer {:mvn/version "1.0.7075"}
            #_[hyperfiddle.electric3 :as e] ; not allowed in .clj
            ))

(def mbrainz-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")
(def test-conn (delay (d/connect mbrainz-uri)))
(def test-db (delay (d/db @test-conn)))
(def lennon 527765581346058) ; datomic pro
(def pour-lamour 17592186058336)
(def cobblestone 17592186068764)
(def yanne 778454232478138)

(comment
  (d/touch (d/entity @test-db pour-lamour))
  := {:db/id 17592186058336,
      :abstractRelease/gid #uuid"f05a1be3-e383-4cd4-ad2a-150ae118f622",
      :abstractRelease/name "Pour l’amour des sous / Parle au patron, ma tête est malade",
      :abstractRelease/type :release.type/single,
      :abstractRelease/artists #{#:db{:id 778454232478138} #:db{:id 580542139477874}},
      :abstractRelease/artistCredit "Jean Yanne & Michel Magne"})
