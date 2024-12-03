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
   {:db/ident :staff/restrictions :db/valueType :db.type/ref :db/cardinality :db.cardinality/many :db/isComponent true :db/doc "Active restrictions for this staff member"}
   {:db/ident :staff/shifts :db/valueType :db.type/ref :db/cardinality :db.cardinality/many :db/isComponent true :db/doc "History of completed shifts"}

   {:db/ident :shift/venue :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Venue where shift was worked"}
   {:db/ident :shift/date :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "Date of shift"}
   {:db/ident :shift/role :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Role performed during shift"}
   {:db/ident :shift/rating :db/valueType :db.type/float :db/cardinality :db.cardinality/one :db/doc "Rating received for this shift"}
   {:db/ident :shift/feedback :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Feedback from venue"}

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
   {:db/ident :restriction/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "When restriction was created"}
   {:db/ident :restriction/expires-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "When restriction expires (if temporary)"}

   {:db/ident :staff/documents :db/valueType :db.type/ref :db/cardinality :db.cardinality/many :db/isComponent true :db/doc "Staff member's documents and certifications"}
   {:db/ident :document/id :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity :db/doc "Unique identifier for document"}
   {:db/ident :document/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Type of document (e.g., :cert.food-handler, :cert.alcohol-service)"}
   {:db/ident :document/name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Display name of document"}
   {:db/ident :document/expiry :db/valueType :db.type/instant :db/cardinality :db.cardinality/one :db/doc "Document expiration date"}
   {:db/ident :document/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Current status of document (e.g., :valid, :expired, :pending)"}

   {:db/ident :staff/roles :db/valueType :db.type/ref :db/cardinality :db.cardinality/many :db/doc "Staff member's qualified roles"}
   {:db/ident :role/bartender :db/doc "Can serve alcoholic beverages"}
   {:db/ident :role/server :db/doc "Can serve food and non-alcoholic beverages"}
   {:db/ident :role/security :db/doc "Can perform security and safety duties"}
   {:db/ident :role/doorman :db/doc "Can manage venue entry and access"}
   {:db/ident :role/host :db/doc "Can manage guest relations and seating"}
   {:db/ident :role/coordinator :db/doc "Can manage event logistics and staff"}
   {:db/ident :role/trainer :db/doc "Can train other staff members"}
   {:db/ident :role/barback :db/doc "Can assist bartenders and manage bar inventory"}

   {:db/ident :venue/id :db/valueType :db.type/long :db/cardinality :db.cardinality/one :db/unique :db.unique/identity :db/doc "Unique identifier for venue"}
   {:db/ident :venue/name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Name of venue"}
   {:db/ident :venue/email :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value :db/doc "Primary contact email"}
   {:db/ident :venue/phone :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/doc "Primary contact phone"}
   {:db/ident :venue/type :db/valueType :db.type/ref :db/cardinality :db.cardinality/one :db/doc "Type of venue"}
   {:db/ident :venue/capacity :db/valueType :db.type/long :db/cardinality :db.cardinality/one :db/doc "Maximum venue capacity"}
   {:db/ident :venue/rating :db/valueType :db.type/float :db/cardinality :db.cardinality/one :db/doc "Average rating from staff"}
   {:db/ident :venue/notify-method :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one :db/doc "Preferred notification method"}

   ;; Venue types as entities
   {:db/ident :venue.type/concert-hall :db/doc "Concert and performance venue"}
   {:db/ident :venue.type/convention-center :db/doc "Large convention and exhibition space"}
   {:db/ident :venue.type/stadium :db/doc "Sports and large event stadium"}
   {:db/ident :venue.type/hotel :db/doc "Hotel event spaces"}
   {:db/ident :venue.type/restaurant :db/doc "Restaurant or dining establishment"}])

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
                       :document/status :valid}]}

   {:venue/id 2001
    :venue/name "Metro Convention Center"
    :venue/email "events@metrocc.com"
    :venue/phone "(415)555-2001"
    :venue/type :venue.type/convention-center
    :venue/capacity 5000
    :venue/rating 4.8
    :venue/notify-method :email}

   {:venue/id 2002
    :venue/name "Riverside Arena"
    :venue/email "staff@riversidearena.com"
    :venue/phone "(415)555-2002"
    :venue/type :venue.type/stadium
    :venue/capacity 15000
    :venue/rating 4.7
    :venue/notify-method :app}

   {:db/id "venue-2003"
    :venue/id 2003
    :venue/name "Grand Concert Hall"
    :venue/email "booking@grandconcert.com"
    :venue/phone "(415)555-2003"
    :venue/type :venue.type/concert-hall
    :venue/capacity 2500
    :venue/rating 4.9
    :venue/notify-method :email}

   {:venue/id 2004
    :venue/name "Luxury Heights Hotel"
    :venue/email "events@luxuryheights.com"
    :venue/phone "(415)555-2004"
    :venue/type :venue.type/hotel
    :venue/capacity 800
    :venue/rating 4.85
    :venue/notify-method :sms}

   {:venue/id 2005
    :venue/name "Harbor View Restaurant"
    :venue/email "staff@harborview.com"
    :venue/phone "(415)555-2005"
    :venue/type :venue.type/restaurant
    :venue/capacity 200
    :venue/rating 4.75
    :venue/notify-method :app}

   {:staff/id 1001
    :staff/restrictions
    [{:restriction/venue "venue-2003"
      :restriction/reason :no-show
      :restriction/scope :all-roles
      :restriction/created-at #inst "2024-01-15"
      :restriction/expires-at #inst "2024-07-15"}]
    :staff/shifts
    [{:shift/venue "venue-2003"
      :shift/date #inst "2024-03-15"
      :shift/role :role/bartender
      :shift/rating 4.8
      :shift/feedback "Excellent service, very professional"}]}


   ])