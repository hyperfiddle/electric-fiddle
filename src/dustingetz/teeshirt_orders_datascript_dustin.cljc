(ns dustingetz.teeshirt-orders-datascript-dustin
  (:require [datascript.core :as d]
            [datascript.impl.entity :as de]  ; for `entity?` predicate
            [hyperfiddle.rcf :refer [tests % tap]]))

(def schema ; user orders a tee-shirt and select a tee-shirt gender and size + optional tags
  ;; :hf/valueType is an annotation, used by hyperfiddle to auto render a UI (To be improved. Datascript only accepts :db/valueType on refs)
  {:order/email      {:hf/valueType :db.type/string, :db/cardinality :db.cardinality/one, :db/unique :db.unique/identity}
   :order/gender     {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/one}
   :order/shirt-size {:db/valueType :db.type/ref, :db/cardinality :db.cardinality/one}
   :order/type       {:db/cardinality :db.cardinality/one}
   :order/tags       {:db/cardinality :db.cardinality/many}
   :db/ident         {:db/unique :db.unique/identity, :hf/valueType :db.type/keyword}})

(def fixtures-genders
  [{:db/id 1, :order/type :order/gender, :db/ident :order/male} ; straight cut
   {:db/id 2, :order/type :order/gender, :db/ident :order/female}]) ; fitted
(def fixtures-shirt-sizes
  [{:db/id 3 :order/type :order/shirt-size :db/ident :order/mens-small :order/gender :order/male}
   {:db/id 4 :order/type :order/shirt-size :db/ident :order/mens-medium :order/gender :order/male}
   {:db/id 5 :order/type :order/shirt-size :db/ident :order/mens-large :order/gender :order/male}
   {:db/id 6 :order/type :order/shirt-size :db/ident :order/womens-small :order/gender :order/female}
   {:db/id 7 :order/type :order/shirt-size :db/ident :order/womens-medium :order/gender :order/female}
   {:db/id 8 :order/type :order/shirt-size :db/ident :order/womens-large :order/gender :order/female}])
(def fixtures-alice-bob-charlie
  [{:db/id 9,  :order/email "alice@example.com",   :order/gender :order/female,  :order/shirt-size :order/womens-large, :order/tags  [:a :b :c]}
   {:db/id 10, :order/email "bob@example.com",     :order/gender :order/male,    :order/shirt-size :order/mens-large,   :order/tags  [:b]}
   {:db/id 11, :order/email "charlie@example.com", :order/gender :order/male,    :order/shirt-size :order/mens-medium}])

(defn fixtures [db]
  (-> db
    (d/with fixtures-genders) :db-after
    (d/with fixtures-shirt-sizes) :db-after
    (d/with fixtures-alice-bob-charlie) :db-after))

(declare conn)

(let [conn (delay (def conn (d/create-conn schema))
             @(d/transact conn fixtures-genders)
             @(d/transact conn fixtures-shirt-sizes)
             @(d/transact conn fixtures-alice-bob-charlie)
             conn)]
  (defn ensure-db! []
    #_(alter-var-root #'hf/*$* (constantly (fixtures (d/db conn))))
    @conn))

#_(def db hf/*$*) ; for @(requiring-resolve 'user.example-datascript-db/db)

(defn get-schema [db a] (get (:schema db) a))

(defn nav!
  ([_ e] e)
  ([db e a] (let [v (a (if (de/entity? e) e (d/entity db e)))]
              (if (de/entity? v)
                (or (:db/ident v) (:db/id v))
                v)))
  ([db e a & as] (reduce (partial nav! db) (nav! db e a) as)))

(def male    1 #_:order/male   #_17592186045418)
(def female  2 #_:order/female #_17592186045419)
(def m-sm    3  #_17592186045421)
(def m-md    4  #_nil)
(def m-lg    5  #_nil)
(def w-sm    6  #_nil)
(def w-md    7  #_nil)
(def w-lg    8  #_nil)
(def alice   9  #_17592186045428)
(def bob     10 #_nil)
(def charlie 11 #_nil)

(defn teeshirt-orders [db ?email-search & [sort-directve]]
  (let [kf (cond
             (vector? sort-directve) #((reduce comp (reverse sort-directve)) (d/touch (d/entity db %)))
             (fn? sort-directve) #(sort-directve (d/touch (d/entity db %)))
             () identity)]
    (sort-by kf
      (d/q '[:find [?e ...] :in $ ?email-search :where
             [?e :order/email ?s]
             [(clojure.string/includes? ?s ?email-search)]]
        db (or ?email-search "")))))

(defn genders
  ([db] (genders db nil))
  ([db ?gender-search]
   (->> (d/q '[:find [?gg ...] :in $ ?gender-search :where
               [_ :order/gender ?g] [?g :db/ident ?gg] ; todo check datascript/datomic compat
               [(clojure.string/includes? ?gg ?gender-search)]]
          db (or ?gender-search ""))
     sort (into []))))

(defn resolve-ref [db x] (:db/id (d/entity db #_@conn x))) ; hax

(defn shirt-sizes [db gender search]
  (let [gender (resolve-ref db gender)]
    (sort
      (if gender
        (d/q '[:in $ ?g ?search :find [?ss ...] ; return idents
               :where
               [?s :order/type :order/shirt-size]
               [?s :order/gender ?g]
               [?s :db/ident ?ss] [(contrib.str/includes-str? ?ss ?search)]]
          db gender (or search ""))
        (d/q '[:in $ ?search :find [?ss ...] :where
               [?s :order/type :order/shirt-size]
               [?s :db/ident ?ss] [(contrib.str/includes-str? ?ss ?search)]]
          db (or search ""))))))

(tests (ensure-db!)) ; test db - intended to have simple fixtures only

(tests
  (teeshirt-orders @conn "" [:order/email])
  (teeshirt-orders @conn "" [:db/id])
  (teeshirt-orders @conn "" [:order/shirt-size :db/ident])
  (teeshirt-orders @conn "" [:order/gender :db/ident])
  (teeshirt-orders @conn "bob")
  (genders @conn)
  (genders @conn "female") := [:order/female]
  (shirt-sizes @conn nil "")
  (shirt-sizes @conn 2 "")
  (shirt-sizes @conn :order/female "")

  (resolve-ref @conn 2) := 2
  (resolve-ref @conn :order/female) := 2)

(comment
  (hyperfiddle.rcf/enable!))

(tests
  (def e [:order/email "alice@example.com"])

  (tests
    "(d/pull ['*]) is best for tests"
    (d/pull db ['*] e)
    := {:db/id            9,
        :order/email      "alice@example.com",
        :order/shirt-size #:db{:id 8},
        :order/gender     #:db{:id 2}
        :order/tags [:a :b :c]})

  (comment #_tests
    "careful, entity type is not= to equivalent hashmap"
    (d/touch (d/entity db e))
    ; expected failure
    := {:order/email      "alice@example.com",
        :order/gender     #:db{:id 2},
        :order/shirt-size #:db{:id 8},
        :order/tags       #{:c :b :a},
        :db/id            9})

  (tests
    "entities are not maps"
    (type (d/touch (d/entity db e)))
    *1 := datascript.impl.entity.Entity)             ; not a map

  (comment #_tests
    "careful, entity API tests are fragile and (into {}) is insufficient"
    (->> (d/touch (d/entity db e))                      ; touch is the best way to inspect an entity
      (into {}))                                         ; but it's hard to convert to a map...
    := #:order{#_#_:id 9                                    ; db/id is not present!
               :email      "alice@example.com",
               :gender     _ #_#:db{:id 2},                 ; entity ref not =, RCF can’t unify with entities
               :shirt-size _ #_#:db{:id 8},                 ; entity ref not =
               :tags       #{:c :b :a}}

    "select keys doesn't fix the problem as it's not recursive"
    (-> (d/touch (d/entity db e))
      (select-keys [:order/email :order/shirt-size :order/gender]))
    := #:order{:email "alice@example.com",
               :shirt-size _ #_#:db{:id 8},                 ; still awkward, need recursive pull
               :gender _ #_#:db{:id 2}})                    ; RCF can’t unify with an entities

  "TLDR is use (d/pull ['*]) like the first example"
  (tests
    (d/pull db ['*] :order/female)
    := {:db/id female :db/ident :order/female :order/type :order/gender})

  (tests
    (d/q '[:find [?e ...] :where [_ :order/gender ?e]] db)
    := [2 1] #_[:order/male :order/female]))
