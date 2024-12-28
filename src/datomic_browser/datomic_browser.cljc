(ns datomic-browser.datomic-browser
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [contrib.data :refer [unqualify treelister]]
            [contrib.str :refer [any-matches?]]
            #?(:clj [contrib.datomic-contrib :as dx])
            #?(:clj [contrib.datomic-m :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :as forms :refer [Input! Form! Checkbox]]
            [hyperfiddle.electric-scroll0 :as scroll :refer [Scroll-window Spool]]
            [hyperfiddle.router3 :as r]
            [missionary.core :as m]))

(e/defn Scroll-indexed-headless ; legacy
  "random access (fixed height, counted, indexed)"
  [viewport-node xs!
   #_& {:keys [record-count row-height overquery-factor]
        :or {overquery-factor 1}}]
  (let [record-count (or record-count (count xs!))
        row-height (check row-height) ; todo measure, account for browser zoom level
        [offset limit] (Scroll-window row-height record-count viewport-node {:overquery-factor overquery-factor})]
    {::Spool (e/fn [] (Spool record-count xs! offset limit)) ; site neutral, caller chooses
     ::Offset (e/fn [] ; isolate animating value to not rebuild hashmap - micro optimization
                (identity offset)) ; experimental: allow user to artificially delay offset if needed for UX
     ::limit limit ::record-count record-count ::row-height row-height}))

(e/declare conn)
(e/declare db)
(e/declare schema)

(e/defn Grid [xs! #_& {::keys [Row] :as props}]
  (dom/div (dom/props {:class (or (::dom/class props) "Viewport")})
    (let [{::keys [row-height Offset limit record-count Spool]}
          (Scroll-indexed-headless dom/node xs!
            (assoc props :row-height 24 :overquery-factor 1))]
      (dom/table (dom/props {:style {:top (str (* (Offset) row-height) "px")}})
        (e/for [[i x] (e/server (Spool))]
          (dom/tr (Row x))))
      (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}})))))

(e/defn Attributes []
  (dom/h1 (dom/text "Attributes — Datomic Browser"))
  (r/focus [0]
    (e/server
      (Grid (->> (dx/attributes> db
                   [:db/ident {:db/valueType [:db/ident]} {:db/cardinality [:db/ident]} :db/unique :db/isComponent
                    #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs])
              (m/reduce conj []) e/Task (sort-by :db/ident))
        {::Row (e/fn [x]
                 (dom/td (let [v (:db/ident x)] (r/link ['.. '.. [:attribute v]] (dom/text v))))
                 (dom/td (some-> x :db/valueType :db/ident name dom/text))
                 (dom/td (some-> x :db/cardinality :db/ident name dom/text))
                 (dom/td (some-> x :db/unique :db/ident name dom/text))
                 (dom/td (some-> x :db/isComponent :db/ident name dom/text)))}))))

(e/defn AttributeDetail []
  (let [[a _] r/route]
    (dom/h1 (dom/text "Attribute detail: " (pr-str a)))
    (r/focus [1]
      (e/server
        (Grid (->> (d/datoms> db {:index :aevt, :components [a]}) (m/reduce conj []) e/Task)
          {::Row (e/fn [[e _ v tx op]] ; possible destr glitch
                   (dom/td (r/link ['.. '.. [:entity e]] (dom/text e)))
                   (dom/td (dom/text (pr-str a)) #_(let [aa (e/server (e/Task (dx/ident! db aa)))] aa))
                   (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
                   (dom/td (r/link ['.. '.. [:tx-detail tx]] (dom/text tx))))})))))

(e/defn TxDetail []
  (let [[e _] r/route]
    (dom/h1 (dom/text "Tx detail: " e))
    (r/focus [1]
      (e/server
        (Grid (->> (d/tx-range> conn {:start e, :end (inc e)}) ; global
                (m/eduction (map :data) cat) (m/reduce conj []) e/Task)
          {::Row (e/fn [[e aa v tx op]] ; possible destr glitch
                   (dom/td (let [e (e/server (e/Task (dx/ident! db e)))] (r/link ['.. '.. [:entity e]] (dom/text e))))
                   (dom/td (let [aa (e/server (e/Task (dx/ident! db aa)))] (r/link ['.. '.. [:attribute aa]] (dom/text aa))))
                   (dom/td (dom/text (pr-str v))) ; when a is ref, render link
                   (dom/td (r/link ['.. '.. [:tx-detail tx]] (dom/text tx))))})))))

#?(:clj (defn easy-attr [schema k]
          ((juxt
             (comp unqualify dx/identify :db/valueType)
             (comp unqualify dx/identify :db/cardinality))
           (k schema))))

(e/defn Format-entity [[tab [k v :as row]]]
  ; keep vals on server, row can contain refs
  (when (some? row)
    (dom/td (cond
              (= :db/id k) (dom/text k) ; :db/id is our schema extension, can't nav to it
              (e/server (contains? schema k)) (r/link ['.. '.. [:attribute k]] (dom/text k))
              () (dom/text (str k)))) ; str is needed for Long db/id, why?
    (dom/td (if-not (coll? v) ; don't render card :many intermediate row
              (let [[valueType cardinality] (e/server (easy-attr schema k))]
                (cond
                  (= :db/id k) (r/link ['.. '.. [:entity v]] (dom/text v))
                  (= :ref valueType) (r/link ['.. '.. [:entity v]] (dom/text v))
                  (= :string valueType) (dom/text v) #_(Form! (Input! k v) :commit (fn [] []) :show-buttons :smart)
                  () (dom/text (pr-str v))))))))

(e/defn EntityDetail []
  (let [[e _] r/route]
    (r/focus [1]
      (dom/h1 (dom/text "Entity detail: " e))
      (e/server
        (Grid (seq ((treelister (partial dx/entity-tree-entry-children schema) any-matches?
                      (e/Task (d/pull db {:eid e :selector ['*] :compare compare}))) "")) ; TODO inject sort
          {:columns [::k ::v]
           ::dom/class "Viewport entity-detail"
           ::Row Format-entity})))))

(comment
  (def schema (m/? (dx/schema! models.mbrainz/*datomic-db*)))
  (def xs (m/? (d/pull models.mbrainz/*datomic-db* {:eid 17592186058296 :selector ['*] :compare compare})))
  (def q (treelister (partial dx/entity-tree-entry-children schema) any-matches? xs))
  (q ""))

(e/defn Format-history-row [[e aa v tx op :as row]]
  (when row ; glitch
    (dom/td (e/client (dom/text (name (case op true :db/add false :db/retract (e/amb))))))
    (dom/td (e/client (r/link ['.. '.. [:entity e]] (dom/text e)))) ; link two levels up because we are under EntityHistory's scope
    (dom/td (if (some? aa)
              (let [ident (:db/ident (e/Task (d/pull db {:eid aa :selector [:db/ident]})))]
                (dom/text (pr-str ident)))))
    (dom/td (some-> v pr-str dom/text))
    (dom/td (e/client (r/link ['.. '.. [:tx-detail tx]] (dom/text tx))))
    (dom/td  (let [x (:db/txInstant (e/Task (d/pull db {:eid tx :selector [:db/txInstant]})))]
               (dom/text (e/client (pr-str x)))))))

(e/defn EntityHistory []
  (let [[e _] r/route]
    (r/focus [1]
      (dom/h1 (dom/text "Entity history: " e))
      (e/server
        (Grid (->> (dx/entity-history-datoms> db e) (m/reduce conj []) e/Task)
          {:columns [::e ::a ::op ::v ::tx-instant ::tx]
           ::dom/class "Viewport entity-history"
           ::Row Format-history-row})))))

(e/defn DbStats []
  (dom/h1 (dom/text "Db stats"))
  (r/focus [0]
    (e/server
      (Grid (seq ((treelister (fn [[k v]] (condp = k :attrs (into (sorted-map) v) nil)) any-matches?
                    (e/Task (d/db-stats db))) ""))
        {::Row (e/fn [[tab [k v]]] ; thead: [::k ::v]
                 (dom/td (dom/text (pr-str k)) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
                 (dom/td (cond
                           (= k :attrs) nil                ; print children instead
                           () (dom/text (pr-str v)))))}))))

(comment
  {:datoms 800958,
   :attrs
   {:release/script {:count 11435},
    :label/type {:count 870}
    ... ...}})

(e/defn RecentTx []
  (r/focus [0]
    (dom/h1 (dom/text "Recent Txs"))
    (e/server
      (Grid (->> (d/datoms> db {:index :aevt, :components [:db/txInstant]}) (m/reduce conj ()) e/Task)
        {::Row (e/fn [[e _ v tx op :as record]]
                 ; columns [:db/id :db/txInstant]
                 (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
                 (dom/td (dom/text (e/client (pr-str v)) #_(e/client (.toLocaleDateString v)))))}))))

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:attributes]] (dom/text "home")) (dom/text " ")
    (r/link ['.. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
    (r/link ['.. [:recent-tx]] (dom/text "recent-tx"))))

(declare css)
(e/defn Page []
  (dom/style (dom/text css))
  (dom/props {:class "DatomicBrowser"})
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:attributes]]))
    (dom/props {:class page})
    (r/pop
      (Nav)
      (case page
        :attributes (Attributes)
        :attribute (AttributeDetail)
        :tx-detail (TxDetail)
        :entity (EntityDetail) #_(EntityHistory) ; todo layout two grids at once
        #_#_:entity (dom/div (dom/props {:class "entity-wrap"})
                  (dom/div (EntityDetail)) (dom/div (EntityHistory))) ; todo untangle router inputs
        :db-stats (DbStats)
        :recent-tx (RecentTx)
        (e/amb)))))

(e/defn DatomicBrowser [conn]
  (e/client
    (let [fail true #_(dom/div (Checkbox true :label "failure"))
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

(def css "
.DatomicBrowser .Viewport { overflow-x:hidden; overflow-y:auto; position:fixed; top:8em; bottom:0; left:0; right:0; }
.DatomicBrowser table { position: relative; display: grid; }
.DatomicBrowser table tr { display: contents; }
.DatomicBrowser table tr:nth-child(even) td { background-color: #f2f2f2; }
.DatomicBrowser table tr:hover td { background-color: #ddd; }
.DatomicBrowser table td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.DatomicBrowser.attributes table { grid-template-columns: auto 6em 4em 4em 4em; }
.DatomicBrowser.attribute table { grid-template-columns: 15em 15em calc(100% - 15em - 15em - 9em) 9em; }
.DatomicBrowser.tx-detail table { grid-template-columns: 15em 15em calc(100% - 15em - 15em - 9em) 9em; }
.DatomicBrowser.recent-tx table { grid-template-columns: 10em auto; }
.DatomicBrowser.db-stats table { grid-template-columns: 20em auto; }
.DatomicBrowser.entity .entity-detail table { grid-template-columns: 15em auto; }
.DatomicBrowser.entity .entity-history table { grid-template-columns: 10em 10em 3em auto auto 9em; }

/*
.DatomicBrowser.entity > div.entity-wrap { display: grid; grid-template-rows: 1fr 1fr; position: fixed; top: 8em; bottom: 0; left: 0; right: 0; }
.DatomicBrowser.entity .Viewport2 { overflow-x:hidden; overflow-y:auto; }
.DatomicBrowser.entity .Viewport2 table { position: relative; display: grid; } */
")