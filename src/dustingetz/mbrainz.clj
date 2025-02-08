(ns dustingetz.mbrainz
  (:require [datomic.api :as d] ; com.datomic/peer {:mvn/version "1.0.7075"}
            #_[hyperfiddle.electric3 :as e] ; not allowed in .clj
            [hyperfiddle.rcf :refer [tests]]))

(def mbrainz-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")
(def test-conn (delay (d/connect mbrainz-uri)))
(def test-db (delay (d/db @test-conn)))
(def lennon 527765581346058) ; datomic pro
(def pour-lamour 17592186058336)
(def cobblestone 17592186068764)
(def yanne 778454232478138)

(tests
  (d/pull @test-db [:db/id :abstractRelease/name :abstractRelease/artistCredit] pour-lamour)
  := {:db/id pour-lamour,
      :abstractRelease/name "Pour l’amour des sous / Parle au patron, ma tête est malade",
      :abstractRelease/artistCredit "Jean Yanne & Michel Magne"})
