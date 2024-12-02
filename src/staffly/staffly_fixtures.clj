(ns staffly.staffly-fixtures
  (:require [datomic.api :as d]
            [missionary.core :as m]))

(def schema
  [{:db/ident :staff/id :db/valueType :db.type/long :db/cardinality :db.cardinality/one :db/unique :db.unique/identity :db/doc "Unique identifier for staff member"}
   {:db/ident :staff/name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Full name of staff member"}
   {:db/ident :staff/email :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value :db/doc "Email address of staff member"}
   {:db/ident :staff/phone :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Contact phone number"}
   {:db/ident :staff/notify-method :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Preferred notification method (e.g., :sms, :email, :app)"}
   {:db/ident :staff/roles :db/valueType :db.type/ref :db/cardinality :db.cardinality/many :db/doc "Staff member's qualified roles"}
   {:db/ident :staff/events-worked :db/valueType :db.type/long :db/cardinality :db.cardinality/one :db/doc "Number of events completed"}
   {:db/ident :staff/venue-rating :db/valueType :db.type/float :db/cardinality :db.cardinality/one :db/doc "Average rating from venues"}
   {:db/ident :staff/punctuality-score :db/valueType :db.type/float :db/cardinality :db.cardinality/one :db/doc "Score based on arrival times and reliability"}

   {:db/ident :document/id :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity :db/doc "Unique identifier for document"}
   {:db/ident :document/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Type of document (e.g., :cert.food-handler, :cert.alcohol-service)"}
   {:db/ident :document/name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Display name of document"}
   {:db/ident :document/expiry :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "Document expiration date"}
   {:db/ident :document/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Current status of document (e.g., :valid, :expired, :pending)"}
   {:db/ident :document/staff :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Reference to staff member who owns this document"}

   {:db/ident :restriction/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity :db/doc "Unique identifier for restriction"}
   {:db/ident :restriction/staff :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Reference to restricted staff member"}
   {:db/ident :restriction/venue :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Reference to venue imposing restriction"}
   {:db/ident :restriction/reason :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Reason for restriction (e.g., :no-show, :conduct, :safety)"}
   {:db/ident :restriction/scope :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Scope of restriction (e.g., :all-roles, :role-specific, :event-type)"}
   #_{:db/ident :restriction/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "When restriction was created"}
   #_{:db/ident :restriction/expires-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "When restriction expires (if temporary)"}])

(def fixtures
  [;; Sarah Chen - Bartender/Server
   {:staff/id 1001
    :staff/name "Sarah Chen"
    :staff/email "schen@staffly.com"
    :staff/phone "(415)555-0101"
    :staff/notify-method :app
    :staff/events-worked 47
    :staff/venue-rating 4.92
    :staff/punctuality-score 4.88
    :staff/roles [:role/bartender :role/server]
    :staff/documents [{:document/id "cert/food-handler-1001"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-12-15"
                       :document/status :valid}
                      {:document/id "cert/alcohol-service-1001"
                       :document/type :cert.alcohol-service
                       :document/name "Alcohol Service License"
                       :document/expiry #inst "2025-01-20"
                       :document/status :valid}]}

   ;; Marcus Johnson - Security/Doorman
   {:staff/id 1002
    :staff/name "Marcus Johnson"
    :staff/email "mjohnson@staffly.com"
    :staff/phone "(415)555-0102"
    :staff/notify-method :sms
    :staff/events-worked 156
    :staff/venue-rating 4.85
    :staff/punctuality-score 4.95
    :staff/roles [:role/security :role/doorman]
    :staff/documents [{:document/id "cert/security-license-1002"
                       :document/type :cert.security-license
                       :document/name "Security Guard License"
                       :document/expiry #inst "2024-11-30"
                       :document/status :valid}
                      {:document/id "cert/first-aid-1002"
                       :document/type :cert.first-aid
                       :document/name "First Aid Certification"
                       :document/expiry #inst "2024-08-15"
                       :document/status :valid}]}

   ;; Emma Rodriguez - Server/Host
   {:staff/id 1003
    :staff/name "Emma Rodriguez"
    :staff/email "erodriguez@staffly.com"
    :staff/phone "(415)555-0103"
    :staff/notify-method :email
    :staff/events-worked 23
    :staff/venue-rating 4.75
    :staff/punctuality-score 4.90
    :staff/roles [:role/server :role/host]
    :staff/documents [{:document/id "cert/food-handler-1003"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-10-25"
                       :document/status :valid}
                      {:document/id "cert/health-safety-1003"
                       :document/type :cert.health-safety
                       :document/name "Health & Safety Training"
                       :document/expiry #inst "2024-09-30"
                       :document/status :valid}]}

   ;; James Kim - Bartender/Barback/Server
   {:staff/id 1004
    :staff/name "James Kim"
    :staff/email "jkim@staffly.com"
    :staff/phone "(415)555-0104"
    :staff/notify-method :app
    :staff/events-worked 89
    :staff/venue-rating 4.95
    :staff/punctuality-score 4.98
    :staff/roles [:role/bartender :role/barback :role/server]
    :staff/documents [{:document/id "cert/food-handler-1004"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-11-15"
                       :document/status :valid}
                      {:document/id "cert/alcohol-service-1004"
                       :document/type :cert.alcohol-service
                       :document/name "Alcohol Service License"
                       :document/expiry #inst "2024-12-20"
                       :document/status :valid}
                      {:document/id "cert/mixology-1004"
                       :document/type :cert.mixology
                       :document/name "Mixology Certification"
                       :document/expiry #inst "2025-02-28"
                       :document/status :valid}]}

   ;; Aisha Patel - Host/Coordinator
   {:staff/id 1005
    :staff/name "Aisha Patel"
    :staff/email "apatel@staffly.com"
    :staff/phone "(415)555-0105"
    :staff/notify-method :sms
    :staff/events-worked 67
    :staff/venue-rating 4.88
    :staff/punctuality-score 4.92
    :staff/roles [:role/host :role/coordinator]
    :staff/documents [{:document/id "cert/event-safety-1005"
                       :document/type :cert.event-safety
                       :document/name "Event Safety Training"
                       :document/expiry #inst "2024-10-10"
                       :document/status :valid}
                      {:document/id "cert/crowd-mgmt-1005"
                       :document/type :cert.crowd-management
                       :document/name "Crowd Management"
                       :document/expiry #inst "2024-11-25"
                       :document/status :valid}]}

   ;; David O'Connor - Security
   {:staff/id 1006
    :staff/name "David O'Connor"
    :staff/email "doconnor@staffly.com"
    :staff/phone "(415)555-0106"
    :staff/notify-method :app
    :staff/events-worked 12
    :staff/venue-rating 4.65
    :staff/punctuality-score 4.70
    :staff/roles [:role/security]
    :staff/documents [{:document/id "cert/security-license-1006"
                       :document/type :cert.security-license
                       :document/name "Security Guard License"
                       :document/expiry #inst "2024-09-20"
                       :document/status :valid}
                      {:document/id "cert/first-aid-1006"
                       :document/type :cert.first-aid
                       :document/name "First Aid Certification"
                       :document/expiry #inst "2024-08-30"
                       :document/status :valid}]}

   ;; Lisa Thompson - Coordinator/Host/Server
   {:staff/id 1007
    :staff/name "Lisa Thompson"
    :staff/email "lthompson@staffly.com"
    :staff/phone "(415)555-0107"
    :staff/notify-method :email
    :staff/events-worked 234
    :staff/venue-rating 4.97
    :staff/punctuality-score 4.99
    :staff/roles [:role/coordinator :role/host :role/server]
    :staff/documents [{:document/id "cert/food-handler-1007"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-12-05"
                       :document/status :valid}
                      {:document/id "cert/event-safety-1007"
                       :document/type :cert.event-safety
                       :document/name "Event Safety Training"
                       :document/expiry #inst "2024-11-15"
                       :document/status :valid}
                      {:document/id "cert/crowd-mgmt-1007"
                       :document/type :cert.crowd-management
                       :document/name "Crowd Management"
                       :document/expiry #inst "2024-10-20"
                       :document/status :valid}]}

   ;; Michael Chang - Barback/Server
   {:staff/id 1008
    :staff/name "Michael Chang"
    :staff/email "mchang@staffly.com"
    :staff/phone "(415)555-0108"
    :staff/notify-method :sms
    :staff/events-worked 45
    :staff/venue-rating 4.78
    :staff/punctuality-score 4.85
    :staff/roles [:role/barback :role/server]
    :staff/documents [{:document/id "cert/food-handler-1008"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-09-15"
                       :document/status :valid}
                      {:document/id "cert/health-safety-1008"
                       :document/type :cert.health-safety
                       :document/name "Health & Safety Training"
                       :document/expiry #inst "2024-10-30"
                       :document/status :valid}]}

   ;; Sofia Garcia - Bartender/Trainer
   {:staff/id 1009
    :staff/name "Sofia Garcia"
    :staff/email "sgarcia@staffly.com"
    :staff/phone "(415)555-0109"
    :staff/notify-method :app
    :staff/events-worked 178
    :staff/venue-rating 4.91
    :staff/punctuality-score 4.94
    :staff/roles [:role/bartender :role/trainer]
    :staff/documents [{:document/id "cert/food-handler-1009"
                       :document/type :cert.food-handler
                       :document/name "Food Handler Certification"
                       :document/expiry #inst "2024-12-30"
                       :document/status :valid}
                      {:document/id "cert/alcohol-service-1009"
                       :document/type :cert.alcohol-service
                       :document/name "Alcohol Service License"
                       :document/expiry #inst "2025-01-15"
                       :document/status :valid}
                      {:document/id "cert/trainer-1009"
                       :document/type :cert.trainer
                       :document/name "Staff Training Certification"
                       :document/expiry #inst "2025-03-15"
                       :document/status :valid}]}

   ;; Alex Mitchell - Security/Doorman
   {:staff/id 1010
    :staff/name "Alex Mitchell"
    :staff/email "amitchell@staffly.com"
    :staff/phone "(415)555-0110"
    :staff/notify-method :email
    :staff/events-worked 34
    :staff/venue-rating 4.82
    :staff/punctuality-score 4.88
    :staff/roles [:role/security :role/doorman]
    :staff/documents [{:document/id "cert/security-license-1010"
                       :document/type :cert.security-license
                       :document/name "Security Guard License"
                       :document/expiry #inst "2024-10-15"
                       :document/status :valid}
                      {:document/id "cert/first-aid-1010"
                       :document/type :cert.first-aid
                       :document/name "First Aid Certification"
                       :document/expiry #inst "2024-09-30"
                       :document/status :valid}]}])