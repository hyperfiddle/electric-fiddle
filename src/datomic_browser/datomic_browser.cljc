(ns datomic-browser.datomic-browser
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [contrib.data :refer [unqualify treelister]]
            [contrib.str :refer [any-matches?]]
            #?(:clj [contrib.datomic-contrib :as dx])
            #?(:clj [contrib.datomic-m :as d])
            [dustingetz.gridsheet4 :refer [Explorer]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :as forms :refer [Input! Form! Checkbox]]
            [hyperfiddle.router3 :as r]
            [missionary.core :as m]))

(e/declare conn)
(e/declare db)
(e/declare schema)

(e/defn Attributes []
  (dom/h1 (dom/text "Attributes — Datomic Browser"))
  (r/focus [0]
    (let [cols [:db/ident :db/valueType :db/cardinality :db/unique :db/isComponent
                #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs]]
      (Explorer
        (e/server (->> (dx/attributes> db cols) (m/reductions conj []) (m/relieve {}) e/input
                    (sort-by :db/ident) ; slow
                    (treelister (fn [_]) any-matches?)))
        {:page-size 15
         :row-height 24
         :columns cols
         :grid-template-columns "auto 6em 4em 4em 4em"
         :Format
         (e/fn [row col]
           (e/client
             (let [v (col row)]
               (case col
                 :db/ident (r/link ['.. '.. [:attribute v]] (dom/text v))
                 :db/valueType (some-> v :db/ident name dom/text)
                 :db/cardinality (some-> v :db/ident name dom/text)
                 :db/unique (some-> v :db/ident name dom/text)
                 (dom/text (str v))))))}))))

(e/defn AttributeDetail []
  (let [[a _] r/route]
    (dom/h1 (dom/text "Attribute detail: " (pr-str a)))
    (r/focus [1]
      (Explorer
        (e/server (->> (d/datoms> db {:index :aevt, :components [a]})
                    (m/reductions conj []) (m/relieve {}) e/input
                    (treelister (fn [_]) any-matches?)))
        {:page-size 20
         :row-height 24
         :columns [:e :a :v :tx]
         :grid-template-columns "15em 15em calc(100% - 15em - 15em - 9em) 9em"
         :Format
         (e/fn [x k]
           (e/client
             (let [[e _ v tx op] x] ; destructure on client to workaround glitch
               (case k
                 :e (r/link ['.. '.. [:entity e]] (dom/text e))
                 :a (dom/text (pr-str a)) #_(let [aa (e/server (e/Task (dx/ident! db aa)))] aa)
                 :v (some-> v str dom/text) ; todo when a is ref, render link
                 :tx (r/link ['.. '.. [:tx tx]] (dom/text tx))
                 (e/amb)))))}))))

(e/defn TxDetail []
  (let [[e _] r/route]
    (dom/h1 (dom/text "Tx detail: " e))
    (r/focus [1]
      (Explorer
        (e/server
          (->> (d/tx-range> conn {:start e, :end (inc e)}) ; global
            (m/eduction (map :data) cat) (m/reductions conj []) (m/relieve {}) e/input
            (treelister (fn [_]) any-matches?)))
        {:page-size 20
         :row-height 24
         :columns [:e :a :v :tx]
         :grid-template-columns "15em 15em calc(100% - 15em - 15em - 9em) 9em"
         :Format
         (e/fn [x a]
           (e/client
             (let [[e aa v tx op] x] ; workaround glitch
               (case a
                 :e (let [e (e/server (e/Task (dx/ident! db e)))] (r/link ['.. '.. [:entity e]] (dom/text e)))
                 :a (let [aa (e/server (e/Task (dx/ident! db aa)))] (r/link ['.. '.. [:attribute aa]] (dom/text aa)))
                 :v (dom/text (pr-str v))                ; when a is ref, render link
                 (dom/text (str tx))))))}))))

#?(:clj (defn easy-attr [schema k]
          ((juxt
             (comp unqualify dx/identify :db/valueType)
             (comp unqualify dx/identify :db/cardinality))
           (k schema))))

(e/defn Format-entity [row col]
  ; keep vals on server, row can contain refs
  (let [k (e/server (some-> row (nth 0))), v (e/server (some-> row (nth 1)))]
    (when (e/server (some? row))
      (case col
        ::k (cond
              (= :db/id k) (dom/text k) ; :db/id is our schema extension, can't nav to it
              (e/server (contains? schema k)) (r/link ['.. '.. [:attribute k]] (dom/text k))
              () (dom/text (str k))) ; str is needed for Long db/id, why?
        ::v (if-not (e/server (coll? v))           ; don't render card :many intermediate row
              (let [[valueType cardinality] (e/server (easy-attr schema k))]
                (cond
                  (= :db/id k) (r/link ['.. '.. [:entity v]] (dom/text v))
                  (= :ref valueType) (r/link ['.. '.. [:entity v]] (dom/text v))
                  (= :string valueType) (Form! (Input! k v)
                                          :commit (fn [] [])
                                          :show-buttons :smart)
                  () (dom/text (pr-str v)))))
        (e/amb)))))

(e/defn EntityDetail []
  (let [[e _] r/route]
    (r/focus [1]
      (dom/h1 (dom/text "Entity detail: " e))
      (Explorer
        (e/server (->> (e/Task (d/pull db {:eid e :selector ['*] :compare compare})) ; TODO inject sort
                    (treelister (partial dx/entity-tree-entry-children schema) any-matches?)))
        {:page-size 15
         :row-height 24
         :columns [::k ::v]
         :grid-template-columns "15em auto"
         :Format Format-entity}))))

(comment
  (def schema (m/? (dx/schema! models.mbrainz/*datomic-db*)))
  (def xs (m/? (d/pull models.mbrainz/*datomic-db* {:eid 17592186058296 :selector ['*] :compare compare})))
  (def q (treelister (partial dx/entity-tree-entry-children schema) any-matches? xs))
  (q ""))

(e/defn Format-history-row [[e aa v tx op :as row] a]
  (when row          ; when this view unmounts, somehow this fires as nil
    (case a
      ::op (e/client (dom/text (name (case op true :db/add false :db/retract (e/amb)))))
                 ;; link two levels up because we are under EntityHistory's scope
      ::e (e/client (r/link ['.. '.. [:entity e]] (dom/text e)))
      ::a (if (some? aa)
            (let [ident (:db/ident (e/Task (d/pull db {:eid aa :selector [:db/ident]})))]
              (dom/text (pr-str ident))))
      ::v (some-> v pr-str dom/text)
      ::tx (e/client (r/link ['.. '.. [:tx tx]] (dom/text tx)))
      ::tx-instant (let [x (:db/txInstant (e/Task (d/pull db {:eid tx :selector [:db/txInstant]})))]
                     (dom/text (e/client (pr-str x))))
      (dom/text (str v)))))

(e/defn EntityHistory []
  (let [[e _] r/route]
    (r/focus [1]
      (dom/h1 (dom/text "Entity history: " e))
      (Explorer
        (e/server
          ; accumulate what we've seen so far, for pagination. Gets a running count. Bad?
          (->> (dx/entity-history-datoms> db e)
            (m/reductions conj []) ; track a running count as well?
            (m/relieve {}) e/input
            (treelister (fn [_]) any-matches?)))
        {:page-size 20
         :row-height 24
         :columns [::e ::a ::op ::v ::tx-instant ::tx]
         :grid-template-columns "10em 10em 3em auto auto 9em"
         :Format Format-history-row}))))

(e/defn DbStats []
  (dom/h1 (dom/text "Db stats"))
  (r/focus [0]
    (Explorer
      (e/server (->> (e/Task (d/db-stats db))
                  (treelister (fn [[k v]] (condp = k :attrs (into (sorted-map) v) nil)) any-matches?)))
      {:page-size 20
       :row-height 24
       :columns [::k ::v]
       :grid-template-columns "20em auto"
       :Format
       (e/fn [[k v :as row] col]
         (e/client
           (case col
             ::k (dom/text (pr-str k))
             ::v (cond
                   (= k :attrs) nil                ; print children instead
                   () (dom/text (pr-str v)))
             (e/amb))))})))

(comment
  {:datoms 800958,
   :attrs
   {:release/script {:count 11435},
    :label/type {:count 870}
    ... ...}})

(e/defn RecentTx []
  (r/focus [0]
    (dom/h1 (dom/text "Recent Txs"))
    (Explorer
      (e/server (->> (d/datoms> db {:index :aevt, :components [:db/txInstant]})
                  (m/reductions conj ()) (m/relieve {}) e/input
                  (treelister (fn [_]) any-matches?)))
      {:page-size 30
       :row-height 24
       :columns [:db/id :db/txInstant]
       :grid-template-columns "10em auto"
       :Format
       (e/fn [[e _ v tx op :as record] a]
         (case a
           :db/id (e/client (r/link ['.. [::tx tx]] (dom/text tx)))
           :db/txInstant (dom/text (e/client (pr-str v)) #_(e/client (.toLocaleDateString v)))
           (e/amb)))})))

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:attributes]] (dom/text "home")) (dom/text " ")
    (r/link ['.. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
    (r/link ['.. [:recent-tx]] (dom/text "recent-tx"))))

(e/defn Page []
  (dom/props {:class "user-gridsheet-demo"})
  (dom/link (dom/props {:rel :stylesheet, :href "gridsheet-optional.css"}))
  (let [[page] r/route] (when-not page (r/ReplaceState! ['. [:attributes]]))
    (r/pop
      (Nav)
      (case page
        :attributes (Attributes)
        :attribute (AttributeDetail)
        :tx (TxDetail)
        :entity (e/amb (EntityDetail) #_(EntityHistory)) ; todo untangle router inputs
        :db-stats (DbStats)
        :recent-tx (RecentTx)
        (e/amb)))))

(e/defn DatomicBrowser [conn]
  (e/client
    (let [fail (dom/div (Checkbox true :label "failure"))
          edits (bindx [conn conn
                        db (e/server (check (e/Task (d/db conn))))
                        schema (e/server (check (e/Task (dx/schema! db))))]
                  (Page))
          edits (e/Filter some? edits)]
      fail
      (println 'edits (e/Count edits) (e/as-vec edits))
      (e/for [[t form guess] edits]
        (let [res (e/server (if fail ::rejected (do (println form) ::ok)))]
          (case res
            nil (prn 'res-was-nil-stop!)
            ::ok (t)
            (t res)))))))