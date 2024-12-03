(ns staffly.staffly-model
  (:require #?(:clj [contrib.datomic-contrib :as dx])
            #?(:clj [datomic.api :as d])
            [hyperfiddle.electric3 :as e]
            [missionary.core :as m]
            #?(:clj staffly.staffly-fixtures)))

(def ^:dynamic *datomic-conn*)
(def ^:dynamic *db*) ; todo reactive
(def ^:dynamic *schema*)

(def datomic-conn)
(def db)
(def schema)

(def staff-sarah [:staff/id 1001])
(def venue-grand-concert [:venue/id 2003])

#?(:clj (defn create-test-db [& {:keys []}]
          (def test-uri "datomic:mem://staffly")
          (try
            (d/delete-database test-uri)
            (d/create-database test-uri)
            (let [!x (d/connect test-uri)]
              @(d/transact !x staffly.staffly-fixtures/schema)
              (doseq [tx staffly.staffly-fixtures/fixtures]
                @(d/transact !x tx))
              !x)
            (catch Exception e (prn e) ::datomic-create-test-db-failed))))

#?(:clj (defn init-datomic
          [& {:keys []}]
          (m/via m/blk
            (try
              (alter-var-root #'*datomic-conn* (fn [_] (create-test-db)))
              (alter-var-root #'*db* (constantly (d/db *datomic-conn*)))
              (alter-var-root #'*schema* (constantly (m/? (dx/schema! *db*))))
              ::ok (catch Exception e (prn e) ::datomic-unavailable)))))

(comment (m/? (init-datomic)))

(comment
  (d/entity *db* staff-sarah)
  (count (d/q '[:find [?e ...] :where [?e :staff/id]] *db*))
  )