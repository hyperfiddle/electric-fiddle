(ns dustingetz.datomic-contrib
  (:require [contrib.data :refer [index-by unqualify]]
            [dustingetz.datomic-m :as d] ; care
            datomic.api
            [dustingetz.identify :refer [Identifiable]] ; unresolved from electric module, promote ns to root
            [dustingetz.nav-context :refer [NavContext nav-context]]
            #_[hyperfiddle.electric :as e] ; ?
            [hyperfiddle.rcf :refer [tests % tap]]
            [missionary.core :as m]
            [clojure.core.protocols :as ccp :refer [Datafiable]]))

(tests 
  (require '[clojure.datafy :refer [datafy nav]]
           '[dustingetz.mbrainz :refer [test-db lennon pour-lamour yanne cobblestone]])
  (some? @test-db) := true)

; These should use entity API & fetch data when necessary, doing on trees is not ergonomic enough
; (in Hyperfiddle-2020, much complexity stemmed from tree-passing, root cause batch data loading)
(defn identities "select available identifiers from datomic entity, in precedence order"
  [tree & [fallback]] (remove nil? (conj ((juxt :db/ident :db/id) tree) fallback)))

(defn identify "infer canonical identity. If no identity and no fallback, returns input."
  ([tree fallback] (first (identities tree fallback)))
  ([tree] (first (identities tree tree))))

(tests
  (def tree {:db/id 35435060739965075 :db/ident :release.type/single :release.type/name "Single"})
  (identities tree) := [:release.type/single 35435060739965075]
  (identify tree) := :release.type/single

  (tests
    "these are bad; it should query for the canonical identity, not best locally available identity"
    (def tree2 {:db/id 35435060739965075 :release.type/name "Single"})
    (identities tree2) := [35435060739965075]
    (identify tree2) := 35435060739965075)

  "accept fallback value (like keywords)"
  (identify tree 0) := :release.type/single
  (identify tree2 0) := 35435060739965075
  (identify {} 0) := 0

  "No known identifier"
  (identities {}) := []
  (identify {}) := {} ; reuse the input instead of returning nil - experimental
  (identify {} nil) := nil ; explicit nil default

  (index-by :db/id [tree2])   := {35435060739965075 {:db/id 35435060739965075, :release.type/name "Single"}}
  (index-by identify [tree2]) := {35435060739965075 {:db/id 35435060739965075, :release.type/name "Single"}}

  "userland use case - datomic resultset"
  (index-by identify [{:db/id 20512488927800905}
                      {:db/id 68459991991856131}])
  {20512488927800905 #:db{:id 20512488927800905},
   68459991991856131 #:db{:id 68459991991856131}}
  nil)

(defn reverse-attr [?kw]
  (if ?kw
    (keyword (namespace ?kw)
             (let [s (name ?kw)]
               (case (.charAt s 0)
                 \_ (subs s 1)
                 (str "_" s))))))

(tests
  (reverse-attr :foo/bar) := :foo/_bar
  (reverse-attr nil) := nil
  (reverse-attr :foo/_bar) := :foo/bar)

(defn attributes>
  ([db] (attributes> db [:db/ident]))
  ([db pull-pattern]
   (->> (d/qseq {:query '[:find (pull ?e pattern)
                          :in $ pattern
                          :where [?e :db/valueType _]]
                 :args [db pull-pattern]})
        (m/eduction (map first)))))

(tests
  (count (m/? (m/reduce conj [] (attributes> @test-db [:db/ident])))) := 83
  (count (m/? (m/reduce conj [] (attributes> @test-db)))) := 83
  (count (m/? (d/pull @test-db {:eid 50 :selector [:db/ident :db/valueType :db/cardinality]}))) := 3)

(defn schema! [db] ; todo stream
  (m/sp
    (let [>as (attributes> db [:db/ident
                               {:db/valueType [:db/ident]}
                               {:db/cardinality [:db/ident]}])

          ; :db/id is a renderable attr in the semantic UI, schema metadata describes how
          as (cons {:db/ident :db/id
                    :db/cardinality {:db/ident :db.cardinality/one}
                    :db/valueType {:db/ident :db.type/long}
                    #_#_:db/unique :db.unique/identity}
                   (m/? (m/reduce conj [] >as)))]

      ; todo - streaming group-by should be faster – shouldn't need to wait for all attrs to load
      ; todo - only load the attrs that the renderers actually needs
      (index-by :db/ident as))))

#_(defn schema> [db] (e/task->cp (schema! db))) ; no electric here

(tests
  (def test-schema (delay (m/? (schema! @test-db))))
  (:db/ident @test-schema)
  := #:db{:ident :db/ident,
          :valueType #:db{:ident :db.type/keyword},
          :cardinality #:db{:ident :db.cardinality/one}}

  (:db/id @test-schema)
  := #:db{:ident :db/id,
          :cardinality #:db{:ident :db.cardinality/one},
          :valueType #:db{:ident :db.type/long}}

  (count @test-schema) := 84)

(defn entity-history-datoms>
  ([db e] (entity-history-datoms> db e nil))
  ([db ?e ?a]
   (->> (m/ap
          (let [history (m/? (d/history db))
                ; (sequence #_(comp (xf-filter-before-date before)))
                >fwd-xs (d/datoms> history {:index :eavt :components [?e ?a]})
                >rev-xs (d/datoms> history {:index :vaet :components [?e ?a]})]
            (m/amb= (m/?> >fwd-xs) (m/?> >rev-xs)))))))

(comment
  (time (m/? (m/reduce conj [] (entity-history-datoms> @test-db 74766790739005 nil))))
  (time (count (m/? (m/reduce conj [] (entity-history-datoms> @test-db nil nil)))))
  (def it ((entity-history-datoms> @test-db 74766790739005 nil)
           #(println ::notify) #(println ::terminate)))
  @it ; crashes 20250208
  (it))

(defn ident! [db ?e]
  {:pre [db]}
  ; future work - cache idents?
  (m/sp
    (if ?e
      (let [xs (m/? (d/query {:query '[:find ?k :in $ ?e :where [?e :db/ident ?k]]
                              :args [db ?e]}))
            ; datomic onprem: #{[:db.excise/beforeT]}
            ; datomic cloud: [[:db.excise/beforeT]]
            x (ffirst xs)]
        (or x ?e)))))

(comment
  (m/? (d/query {:query '[:find (pull ?e [:db/ident]) ?f :in $ ?e
                          :where [?e :db/ident ?f]]
                 :args [@test-db 17]}))
  := [[#:db{:ident :db.excise/beforeT} :db.excise/beforeT]]

  (m/? (ident! @test-db 17)) := :db.excise/beforeT
  (m/? (ident! @test-db nil)) := nil)

;#?(:clj (defn before? [^java.util.Date a ^java.util.Date b] (<= (.getTime a) (.getTime b))))
;(defn- xf-filter-before-date [before] #?(:clj (filter (fn [[tx e a v added?]] (before? t before)))))

(defn sort-datoms-by-time
  [[tx  e  a  v  added]
   [tx' e' a' v' added']]
  ; tx are monotically increasing right?
  ; Draw :add as more recent than :retract for the same attribute
  (compare [tx' a added']
           [tx a' added]))

;#?(:clj (defn entity-datoms! [db e] (p/chan->task (d/datoms db {:index :eavt, :components [e]}))))
;#?(:clj (defn entity-datoms> [db e] (->> (p/chan->ap (d/datoms db {:index :eavt, :components [e]}))
;                                         (m/eduction (mapcat identity)))))
;#?(:clj (defn entity-datoms< [db a] (->> (entity-datoms> db a)
;                                         (m/reductions conj [])
;                                         (m/latest identity)))) ; FIXME BUFFER
;
;(comment
;  (m/? (entity-datoms! db 1))
;  (m/? (entity-datoms! db :db/ident))
;  (take 3 (m/? (m/reduce conj [] (entity-datoms> db :db/ident)))))


(defn entity-tree-entry-children [schema [k v :as row]] ; row is either a map-entry or [0 {:db/id _}]
  ; This shorter expr works as well but is a bit "lucky" with types in that you cannot see
  ; the intermediate cardinality many traversal. Unclear what level of power is needed here
  ;(cond
  ;  (map? v) (into (sorted-map) v)
  ;  (sequential? v) (index-by identify v))

  ; instead, dispatch on static schema in controlled way to reveal the structure
  (cond
    (contains? schema k)
    (let [x ((juxt (comp unqualify identify :db/valueType)
               (comp unqualify identify :db/cardinality)) (k schema))]
      (case x
        [:ref :one] (into (sorted-map) v) ; todo lift sort to the pull object
        [:ref :many] (index-by identify v) ; can't sort, no sort key
        nil #_(println `unmatched x))) ; no children

    ; in card :many traversals k can be an index or datomic identifier, like
    ; [0 {:db/id 20512488927800905}]
    ; [20512488927800905 {:db/id 20512488927800905}]
    ; [:release.type/single {:db/id 35435060739965075, :db/ident :release.type/single}]
    (number? k) (into (sorted-map) v)

    () (assert false (str "unmatched tree entry, k: " k " v: " v))))

(comment
  ; watch out, test schema needs to match
  (entity-tree-entry-children @test-schema [:db/id 87960930235113]) := nil
  (entity-tree-entry-children @test-schema [:abstractRelease/name "Pour l’amour..."]) := nil
  (entity-tree-entry-children @test-schema [:abstractRelease/type #:db{:id 35435060739965075, :ident :release.type/single}])
  := #:db{:id 35435060739965075, :ident :release.type/single}
  (entity-tree-entry-children @test-schema [:abstractRelease/artists [#:db{:id 20512488927800905}
                                                                     #:db{:id 68459991991856131}]])
  := {20512488927800905 #:db{:id 20512488927800905},
      68459991991856131 #:db{:id 68459991991856131}}

  (def tree (m/? (d/pull @test-db {:eid pour-lamour :selector ['*]})))
  (->> tree (map (partial entity-tree-entry-children @test-schema)))
  := [nil
      nil
      nil
      #:db{:id 35435060739965075, :ident :release.type/single} ; BROKEN TEST
      {20512488927800905 #:db{:id 20512488927800905},
       68459991991856131 #:db{:id 68459991991856131}}
      nil]
  nil)

(defn easy-attr [schema k] ; bad, deprecate, unused?
  ((juxt
     (comp unqualify identify :db/valueType)
     (comp unqualify identify :db/cardinality))
   (k schema)))

(defn easy-attr2 [db ?a] ; better, copied from datomic-browser.datomic-model
  (when ?a
    (let [!e (datomic.api/entity db ?a)]
      [(unqualify (:db/valueType !e))
       (unqualify (:db/cardinality !e))
       (unqualify (:db/unique !e))
       (if (:db/isComponent !e) :component)])))

(tests
  (easy-attr2 @test-db :db/ident) := [:keyword :one :identity nil]
  (easy-attr2 @test-db :artist/name) := [:string :one nil nil])

(defn includes-lowercase? [v needle]
  (clojure.string/includes?
    (.toLowerCase (or (str v) "")) ; v is str or str'able, e.g. keyword
    (.toLowerCase (or needle ""))))

(tests
  (includes-lowercase? "alice@example.com" "Alice") := true
  (includes-lowercase? "alice@example.com" "bob") := false
  (includes-lowercase? "alice@example.com" "") := true
  (includes-lowercase? :tx.district/richardson-allgrades "tx") := true
  (includes-lowercase? 17592193910285 "285") := true
  ;(needle-match 17592193910285 17592193910285) := true -- throws
  )

(defn ea-includes-lowercase? [db e a search]
  (cond
    (instance? clojure.lang.Keyword a) (includes-lowercase? (get (datomic.api/entity db e) a) search)
    (instance? clojure.lang.PersistentHashSet a) (->> a (map #(ea-includes-lowercase? db e % search))
                                                   (reduce #(or %1 %2)))))
(comment
  (require '[swing.suber-model :refer [*datomic-db* swing-edu]])
  (ea-includes-lowercase? swing.suber-model/*datomic-db* 17592193910285 :school/name "") := true ; even if excised
  (ea-includes-lowercase? *datomic-db* swing-edu :school/name "SwingEDU") := true
  (ea-includes-lowercase? swing.suber-model/*datomic-db* 17592193910285 #{:school/name :db/id} "285") := true
  (get (datomic.api/entity *datomic-db* swing-edu) :school/name) := true)

;;; Entity back references

(defn reverse-attribute? [attribute]
  {:pre [(qualified-keyword? attribute)]}
  (clojure.string/starts-with? (name attribute) "_"))

(defn invert-attribute [attribute]
  {:pre [(qualified-keyword? attribute)]}
  (let [nom (name attribute)]
    (keyword (namespace attribute) (if (reverse-attribute? attribute) (subs nom 1) (str "_" nom)))))

(tests
  (reverse-attribute? :foo/bar) := false
  (reverse-attribute? :foo/_bar) := true
  (invert-attribute :abstractRelease/artists) := :abstractRelease/_artists
  (invert-attribute (invert-attribute :abstractRelease/artists)) := :abstractRelease/artists)

(defn find-attr-ident [db attribute-id] ; faster than (:db/ident (d/entity …))
  (:v (first (datomic.api/datoms db :eavt attribute-id))))

(defn reverse-refs
  ([db target] (reverse-refs db target false))
  ([db target include-system-refs?]
   (->> (datomic.api/datoms db :vaet target)
     (eduction (if include-system-refs? ; single conditional check
                 (map identity) ; noop
                 (remove #(zero? ^long (:e %)))) ; should byte-compile to `==`
       (map (fn [datom] [(find-attr-ident db (:a datom)) (:e datom)]))))))

;; (reverse-refs @test-db 527765581346058)

(defn back-references [db eid] ; optimized for speed – returns a map {:reverse/_ref #{entity entity ...}
  (as-> (reverse-refs db eid) % ; return eduction of vector pairs
    (contrib.data/group-by
      #(get % 0) ; fast `first`
      (fn [coll x] (conj! (or coll (transient #{})) (datomic.api/entity db (get x 1)))) ; fast `second`
      %)
    ;; single-pass invert-key + freeze entity sets
    (reduce-kv (fn [r k v] (-> (dissoc! r k) (assoc! (invert-attribute k) (persistent! v))))
      (transient %) %)
    (persistent! %)))

(tests
  (datomic.api/touch (datomic.api/entity @test-db yanne))
  (def !e (back-references @test-db yanne))
  ; WARNING: these are EntityMaps, NOT maps. d/touch returns native objects!
  #_{:abstractRelease/_artists #{#:db{:id 17592186058336} #:db{:id 17592186067319}},
     :release/_artists #{#:db{:id 17592186069801} #:db{:id 17592186080017}},
     :track/_artists #{#:db{:id 1059929209283807}
                       #:db{:id 862017116284124}
                       #:db{:id 862017116284125}
                       #:db{:id 1059929209283808}}}
  (map type (:abstractRelease/_artists !e)) := [datomic.query.EntityMap datomic.query.EntityMap]
  (map type (:release/_artists !e)) := [datomic.query.EntityMap datomic.query.EntityMap]
  (map type (:track/_artists !e)) := [datomic.query.EntityMap datomic.query.EntityMap datomic.query.EntityMap datomic.query.EntityMap])

(comment
  (back-references _db 778454232478138)
  (back-references _db (:db/id _artist_e))
  )

;;; Datafy/Nav

(defn query-schema [db]
  (datomic.api/q '[:find (pull ?attr [*
                            {:db/valueType [:db/ident]
                             :db/cardinality [:db/ident]
                             :db/unique [:db/ident]}])
         :where [:db.part/db :db.install/attribute ?attr]]
    db
    {:limit -1}))

(defn index-schema [schema] (into {} (comp cat (index-by :db/ident)) schema))
(defn ref? [indexed-schema a] (= :db.type/ref (get-in indexed-schema [a :db/valueType :db/ident])))

(extend-type datomic.query.EntityMap
  Identifiable (-identify [^datomic.query.EntityMap !e] (:db/id !e))
  NavContext (-nav-context [entity] {`ccp/nav (fn [e k v] (clojure.datafy/nav entity k v))})
  ccp/Navigable
  (nav [^datomic.query.EntityMap entity k v]
    (let [[typ card unique? comp?] (easy-attr2 (.-db entity) k)]
      (cond
        (#{:db/id :db/ident} k) entity
        ; TODO cache schema?
        (and (keyword? v) (ref? (index-schema (query-schema (.-db entity))) k)) (datomic.api/entity (.-db entity) v) ; traverse ident refs
        (= :identity unique?) (datomic.api/entity (.-db entity) [k v]) ; resolve lookup ref, todo cleanup
        () (k entity v) ; traverse refs or return value
        )))
  ccp/Datafiable
  (datafy [^datomic.query.EntityMap entity]
    (let [db (.-db entity)]
      (-> {:db/id (:db/id entity)}
        (into (datomic.api/touch entity))
        (into (back-references db (:db/id entity))) ; G: not more expansive than d/touch - heavily optimized.
        (with-meta (nav-context entity))
        ))))

(comment (datomic.api/entity @test-db [:abstractRelease/gid #uuid "320eeca0-a5ff-383f-a66e-b2f559ed0ab6"]))

;; Patch EntityMap printing to differentiate it from regular maps
(defonce original-entity-map-print-method (get-method print-method datomic.query.EntityMap))
(defmethod print-method datomic.query.EntityMap [e writer]
  (.write writer "#datomic.query.EntityMap ")
  (binding [*print-namespace-maps* false]
    (original-entity-map-print-method e writer)))

#_ ; BAD FUNCTION, leaving this tombstone to warn the next confused person
(defn untouch-refs [indexed-schema touched-entity] ; only touched attrs are present
  (letfn [(untouch-ref [{id :db/id}] (datomic.api/entity (.-db touched-entity) id))] ; resolve back to underlying for nav
    (reduce-kv
      (fn [acc k v]
        (if (ref? indexed-schema k)
          (cond
            (set? v) (assoc acc k (set (map untouch-ref v)))
            (map? v) (assoc acc k (untouch-ref v))
            :else (assoc acc k v))
          (assoc acc k v)))
      {} touched-entity)))
#_(tests
  #_(def test-schema (delay (index-schema (query-schema @test-db))))
  (def !e (datomic.api/entity @test-db pour-lamour))
  (:abstractRelease/artists (datomic.api/touch !e)) ; := #{#:db{:id 778454232478138} #:db{:id 580542139477874}} -- fail bc entity not= map
  (:abstractRelease/artists (untouch-refs @test-schema (datomic.api/touch (datomic.api/entity @test-db pour-lamour))))
  (map type *1) := [datomic.query.EntityMap datomic.query.EntityMap])


(tests
  (def !pour-lamour (datomic.api/entity @test-db pour-lamour))
  (do (datomic.api/touch !pour-lamour) (datafy !pour-lamour))
  ; RCF crash on '_: java.lang.AbstractMethodError: Receiver class datomic.query.EntityMap does not define or inherit an implementation ...
  #_ {:db/id 17592186058336,
      :abstractRelease/gid #uuid "f05a1be3-e383-4cd4-ad2a-150ae118f622",
      :abstractRelease/name "Pour l’amour des sous / Parle au patron, ma tête est malade",
      :abstractRelease/type :release.type/single,
      :abstractRelease/artists _ ; #{datomic.query.EntityMap datomic.query.EntityMap}
      :abstractRelease/artistCredit "Jean Yanne & Michel Magne"}

  "datomic presents deep refs as native entity, NOT maps" ; WARNING: EntityMap prints as {:db/id 1}, which is incredibly confusing.
  (map type (:abstractRelease/artists (datomic.api/touch !pour-lamour))) := [datomic.query.EntityMap datomic.query.EntityMap]

  "datafy presents deep refs as native entity NOT maps NOT scalars"
  (map type (:abstractRelease/artists (datafy !pour-lamour))) := [datomic.query.EntityMap datomic.query.EntityMap]

  (tests "self-nav resolves the original underlying reference"
    (let [x (as-> (datafy !pour-lamour) x (nav x :db/id (:db/id x)))]
      (type x) := datomic.query.EntityMap
      (= !pour-lamour x) := true))

  (as-> (datafy !pour-lamour) x
    (nav x :abstractRelease/artists (:abstractRelease/artists x))
    (map type x)) := [datomic.query.EntityMap datomic.query.EntityMap] ; prints as #{#:db{:id 778454232478138} #:db{:id 580542139477874}}

  (comment
    (query-schema _db)
    (def _schema (index-schema (query-schema _db)))
    (ref? _schema :db/ident)
    (get-in _schema [:abstractRelease/artists])
    (get-in _schema [:artist/country])))

(tests "sanity tests / docs"
  (tests
    (def !yanne
      (as-> (datafy !pour-lamour) x
        (nav x :abstractRelease/artists (:abstractRelease/artists x)) ; entities yanne and magne
        (index-by :db/id x)
        (nav x 778454232478138 (get x 778454232478138))))

    (type !yanne) := datomic.query.EntityMap
    (:db/id !yanne) := yanne)

  (tests
    (datafy !yanne) ; RCF crashes with java.lang.AbstractMethodError, RCF bug?
    #_{:artist/sortName "Yanne, Jean",
       :artist/name "Jean Yanne",
       :artist/type :artist.type/person,
       :artist/country :country/FR,
       :artist/gid #uuid"da0c147b-2da4-4d81-818e-f2aa9be37f9e",
       :artist/startDay 18,
       :artist/endDay 23,
       :artist/startYear 1933,
       :track/_artists _ ; EntityMap -- #{#:db{:id 1059929209283807} #:db{:id 862017116284124} #:db{:id 862017116284125} #:db{:id 1059929209283808}}
       :artist/endMonth 5,
       :release/_artists _ ; EntityMap -- #{#:db{:id 17592186069801} #:db{:id 17592186080017}}
       :abstractRelease/_artists _ ; EntityMap -- #{#:db{:id 17592186058336} #:db{:id 17592186067319}}
       :artist/endYear 2003,
       :db/id 778454232478138,
       :artist/startMonth 7,
       :artist/gender :artist.gender/male}
    (-> (datafy !yanne) :track/_artists first type) := datomic.query.EntityMap
    (-> (datafy !yanne) :track/_artists count) := 4
    (-> (datafy !yanne) :release/_artists count) := 2
    (-> (datafy !yanne) :abstractRelease/_artists count) := 2)

  (tests
    (def !france (as-> (datafy !yanne) x (nav x :artist/country (:artist/country x))))
    (datafy !france)
    #_{:db/id 17592186045645,
       :db/ident :country/FR,
       :country/name "France",
       :release/_country #{#:db{:id 17592186076553} ...},
       :artist/_country #{#:db{:id 765260092944782} ...},
       :label/_country #{#:db{:id 17592186068643} ...}}
    (-> (datafy !france) :release/_country first type) := datomic.query.EntityMap
    (-> (datafy !france) :release/_country count) := 574
    (-> (datafy !france) :artist/_country count) := 140
    (-> (datafy !france) :label/_country count) := 59))