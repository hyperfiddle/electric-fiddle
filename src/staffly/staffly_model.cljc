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

(def test-uri "datomic:mem://staffly")

#?(:clj (defn init-datomic
          [& {:keys []}]
          (m/via m/blk
            (try
              (d/create-database test-uri)
              (alter-var-root #'*datomic-conn* (constantly (d/connect test-uri)))
              @(d/transact *datomic-conn* staffly.staffly-fixtures/schema)
              @(d/transact *datomic-conn* staffly.staffly-fixtures/fixtures)
              (alter-var-root #'*db* (constantly (d/db *datomic-conn*)))
              (alter-var-root #'*schema* (constantly (m/? (dx/schema! *db*))))
              ::ok (catch Exception e (prn e) ::datomic-unavailable)))))

(comment (m/? (init-datomic)))