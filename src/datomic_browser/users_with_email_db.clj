(ns datomic-browser.users-with-email-db
  (:require [datomic.api :as d]))

;; Create an in-memory database
(def uri "datomic:mem://users-db")
(d/create-database uri)
(def conn (d/connect uri))

;; Define the schema for emails and users
(def schema-tx
  [;; Email schema attributes
   {:db/ident :email/address
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/doc "The email address string"}

   {:db/ident :email/verified
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether the email has been verified"}

   {:db/ident :email/created-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "When the email was added to the system"}

   {:db/ident :email/primary
    :db/valueType :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether this is the user's primary email"}

   ;; User schema attributes
   {:db/ident :user/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/doc "The user's unique ID"}

   {:db/ident :user/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The user's name"}

   {:db/ident :user/email
    :db/valueType :db.type/ref
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/doc "Reference to the user's email"}])

;; Transact the schema
@(d/transact conn schema-tx)

;; Create expanded email entities
(def emails-tx
  [{:db/id "email-1"
    :email/address "john.doe@example.com"
    :email/verified true
    :email/created-at #inst "2024-04-01T10:30:00"
    :email/primary true}

   {:db/id "email-2"
    :email/address "jane.smith@example.com"
    :email/verified true
    :email/created-at #inst "2024-04-02T14:22:00"
    :email/primary true}

   {:db/id "email-3"
    :email/address "bob.johnson@example.com"
    :email/verified false
    :email/created-at #inst "2024-04-05T08:15:00"
    :email/primary true}

   {:db/id "email-4"
    :email/address "alice.williams@example.com"
    :email/verified true
    :email/created-at #inst "2024-03-20T11:45:00"
    :email/primary true}

   {:db/id "email-5"
    :email/address "charlie.brown@example.com"
    :email/verified false
    :email/created-at #inst "2024-04-10T16:30:00"
    :email/primary true}])

;; Transact the emails
(def email-result @(d/transact conn emails-tx))

;; Create user entities with references to emails
(def users-tx
  [{:db/id "user-1"
    :user/id (java.util.UUID/randomUUID)
    :user/name "John Doe"
    :user/email [:email/address "john.doe@example.com"]}

   {:db/id "user-2"
    :user/id (java.util.UUID/randomUUID)
    :user/name "Jane Smith"
    :user/email [:email/address "jane.smith@example.com"]}

   {:db/id "user-3"
    :user/id (java.util.UUID/randomUUID)
    :user/name "Bob Johnson"
    :user/email [:email/address "bob.johnson@example.com"]}

   {:db/id "user-4"
    :user/id (java.util.UUID/randomUUID)
    :user/name "Alice Williams"
    :user/email [:email/address "alice.williams@example.com"]}

   {:db/id "user-5"
    :user/id (java.util.UUID/randomUUID)
    :user/name "Charlie Brown"
    :user/email [:email/address "charlie.brown@example.com"]}])

;; Transact the users
(def users-result @(d/transact conn users-tx))

;; Function to query users with expanded email information
(defn query-users-with-email-details []
  (let [db (d/db conn)
        users (d/q '[:find ?name ?email-address ?verified ?created-at ?primary
                     :where
                     [?u :user/name ?name]
                     [?u :user/email ?e]
                     [?e :email/address ?email-address]
                     [?e :email/verified ?verified]
                     [?e :email/created-at ?created-at]
                     [?e :email/primary ?primary]]
                   db)]
    (println "Users with email details:")
    (doseq [[name email verified created-at primary] users]
      (println name ":")
      (println "  Email:" email)
      (println "  Verified:" verified)
      (println "  Created:" created-at)
      (println "  Primary:" primary)
      (println))))

;; Example of finding unverified emails
(defn find-unverified-emails []
  (let [db (d/db conn)
        unverified-emails (d/q '[:find ?email-address ?user-name
                                 :where
                                 [?e :email/address ?email-address]
                                 [?e :email/verified false]
                                 [?u :user/email ?e]
                                 [?u :user/name ?user-name]]
                               db)]
    (println "Unverified emails:")
    (doseq [[email name] unverified-emails]
      (println email "belongs to" name))))

;; Run the queries
(comment
  (query-users-with-email-details)
  (find-unverified-emails))

;; Example of updating an email verification status
(defn verify-email [email-address]
  (let [email-entity-id (d/q '[:find ?e .
                               :in $ ?email
                               :where [?e :email/address ?email]]
                             (d/db conn) email-address)]
    (when email-entity-id
      @(d/transact conn [[:db/add email-entity-id :email/verified true]])
      (println "Verified email:" email-address))))

(comment
  ;; Verify Bob's email
  (verify-email "bob.johnson@example.com")

  ;; Check that it worked
  (find-unverified-emails))
