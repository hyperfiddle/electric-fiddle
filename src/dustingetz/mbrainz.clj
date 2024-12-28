(ns dustingetz.mbrainz
  (:require [clojure.tools.logging :as log]
            [contrib.assert :refer [check]]
            [datomic.api :as d]
            #_[hyperfiddle.electric3 :as e] ; not allowed in clj file
            [hyperfiddle.rcf :refer [tests]]
            [missionary.core :as m]))

(def datomic-uri "datomic:dev://localhost:4334/mbrainz-1968-1973")

(defn connect []
  (try (check (d/connect datomic-uri))
       (catch Exception e (log/error e) e)))

(comment
  (def conn (connect))
  (def db (d/db conn))
  (d/touch (d/entity db 100))
  := #:db{:id 100,
          :ident :release/name,
          :valueType :db.type/string,
          :cardinality :db.cardinality/one,
          :index true,
          :fulltext true,
          :doc "The name of the release"})

#_#_#_#_
(def ^:dynamic *datomic-client*)
(def ^:dynamic *datomic-conn*)
(def ^:dynamic *datomic-db*)
(def ^:dynamic *schema*)

#_
(defn init-datomic []
  (m/via m/blk
    (try
      (let [conn (d/connect uri)]
        (alter-var-root #'*datomic-conn* (constantly conn))
        (let [db (d/db *datomic-conn*)]
          (alter-var-root #'*datomic-db* (constantly db)))) ; task
      *datomic-conn* (catch Exception e ::datomic-unavailable))))

;; below this point - not sure what this is - DJG 2024-11-6
#_#_#_
(def pour-lamour 87960930235113)
(def cobblestone 536561674378709)

(def datomic-config {:server-type :dev-local :system "datomic-samples"})

#_
(defn install-test-state []
  (alter-var-root #'*datomic-client* (constantly (d/client datomic-config)))
  (assert (some? *datomic-client*))

  (alter-var-root #'*datomic-conn* (constantly (m/? (d/connect *datomic-client* {:db-name "mbrainz-subset"}))))
  (assert (some? *datomic-conn*))

  (alter-var-root #'*datomic-db* (constantly (:db-after (m/? (d/with (m/? (d/with-db *datomic-conn*)) fixtures)))))
  (assert (some? *datomic-db*))

  (alter-var-root #'*schema* (constantly (m/? (dx/schema! *datomic-db*))))
  (assert (some? *schema*)))

#_
(comment tests
  (some? *schema*) := true

  (m/? (d/pull test/datomic-db {:eid pour-lamour :selector ['*]}))
  := {:db/id 87960930235113,
      :abstractRelease/gid #uuid"f05a1be3-e383-4cd4-ad2a-150ae118f622",
      :abstractRelease/name "Pour l’amour des sous / Parle au patron, ma tête est malade",
      :abstractRelease/type #:db{:id 35435060739965075, :ident :release.type/single},
      :abstractRelease/artists [#:db{:id 20512488927800905}
                                #:db{:id 68459991991856131}],
      :abstractRelease/artistCredit "Jean Yanne & Michel Magne"}

  (m/? (d/pull test/datomic-db {:eid cobblestone :selector ['*]}))
  := {:db/id 536561674378709,
      :label/gid #uuid"066474f9-fad7-48dd-868f-04d8bb0a5253",
      :label/name "Cobblestone",
      :label/sortName "Cobblestone",
      :label/type #:db{:id 44604987715616963, :ident :label.type/originalProduction},
      :label/country #:db{:id 63793664643563930, :ident :country/US},
      :label/startYear 1972}
  nil)

; # Scrap / old stuff
;
;* do NOT use mbrainz-1968-1973 (https://github.com/Datomic/mbrainz-importer - don't use this one, too big
;  mbrainz-1968-1973 instructions (don't do this):
;3. Clone this repo:
;4. `cd` into it
;5. create a file `manifest.edn` with this content:
;```clojure
;{:client-cfg {:server-type :dev-local
;              :system      "datomic-samples"}
; :db-name "mbrainz-1968-1973"
; :basedir "subsets"
; :concurrency 3}
;```
;4. In `deps.edn`, set `com.datomic/dev-local` version to `"1.0.243"`
;5. Run `clojure -M -m datomic.mbrainz.importer manifest.edn`