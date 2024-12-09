(ns staffly.staffly-model
  (:require [contrib.assert :refer [check]]
            #?(:clj [contrib.datomic-contrib :as dx])
            #?(:clj [clojure.tools.logging :as log])
            #?(:clj [datomic.api :as d])
            [hyperfiddle.electric3 :as e]
            [missionary.core :as m]
            #?(:clj staffly.staffly-fixtures)))

(def ^:dynamic *datomic-conn*)
(def ^:dynamic *db*) ; todo reactive
(def ^:dynamic *schema*)

(e/declare datomic-conn)
(e/declare db)
(e/declare schema)

(def staff-sarah [:staff/id 1001])
(def venue-grand-concert [:venue/id 2003])

#?(:clj (defn create-test-db [& {:keys []}]
          (def test-datomic-uri "datomic:mem://staffly")
          (d/delete-database test-datomic-uri)
          (d/create-database test-datomic-uri)
          (def test-datomic-conn (d/connect test-datomic-uri))
          @(d/transact test-datomic-conn staffly.staffly-fixtures/schema)
          (doseq [tx staffly.staffly-fixtures/fixtures]
            @(d/transact test-datomic-conn tx))
          test-datomic-conn))

#?(:clj (defn init-datomic
          [& {:keys []}]
          (m/via m/blk
            (try
              (alter-var-root #'*datomic-conn* (fn [_] (create-test-db)))
              (alter-var-root #'*db* (constantly (d/db *datomic-conn*)))
              (alter-var-root #'*schema* (constantly (m/? (dx/schema! *db*))))
              (check *datomic-conn*) (catch Exception e (log/error e) e)))))

(comment (m/? (init-datomic)))

(comment
  (d/entity *db* staff-sarah)
  (count (d/q '[:find [?e ...] :where [?e :staff/id]] *db*))
  )