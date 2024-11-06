(ns datomic-browser.datomic-browser
  (:require [contrib.assert :refer [check]]
            [contrib.data :refer [treelister]]
            [contrib.str :refer [any-matches?]]
            #?(:clj [contrib.datomic-contrib :as dx])
            #?(:clj [contrib.datomic-m :as d])
            [dustingetz.gridsheet3 :as gridsheet :refer [Explorer]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [missionary.core :as m]))

(def conn)
(def db)
(def schema)

(e/defn Attributes []
  (dom/h1 (dom/text "Attributes — " (pr-str r/route)))
  #_(dom/pre (dom/text (pr-str (r/Route-at ['.]))))
  (e/server
    (let [cols [:db/ident :db/valueType :db/cardinality :db/unique :db/isComponent
                #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs]
          xs (->> (dx/attributes> db cols) (m/reductions conj []) (m/relieve {}) e/input)]
      (r/focus [0]
        (Explorer
          (e/Offload #(treelister (sort-by :db/ident xs) (fn [_]) any-matches?))
          {::gridsheet/page-size 15
           ::gridsheet/row-height 24
           ::gridsheet/columns cols
           ::gridsheet/grid-template-columns "auto 6em 4em 4em 4em"
           ::gridsheet/Format
           (e/fn [row col]
             (e/client
               (let [v (col row)]
                 (case col
                   :db/ident (r/link ['.. '.. [:attribute v]] (dom/text v))
                   :db/valueType (some-> v :db/ident name dom/text)
                   :db/cardinality (some-> v :db/ident name dom/text)
                   :db/unique (some-> v :db/ident name dom/text)
                   (dom/text (str v))))))})))))

(e/defn AttributeDetail []
  (let [[a _] r/route]
    (dom/h1 (dom/text "Attribute detail: " (pr-str a)))
    (e/server
      (let [xs (->> (d/datoms> db {:index :aevt, :components [a]})
                 (m/reductions conj []) (m/relieve {}) e/input)]
        (r/focus [1]
          (Explorer
            (e/Offload #(treelister xs (fn [_]) any-matches?))
            {::gridsheet/page-size 20
             ::gridsheet/row-height 24
             ::gridsheet/columns [:e :a :v :tx]
             ::gridsheet/grid-template-columns "15em 15em calc(100% - 15em - 15em - 9em) 9em"
             ::gridsheet/Format
             (e/fn [[e _ v tx op :as x] k]
               (e/client
                 (case k
                   :e (r/link ['.. '.. [:entity e]] (dom/text e))
                   :a (dom/text (pr-str a)) #_(let [aa (e/Task (dx/ident! db aa))] aa)
                   :v (some-> v str dom/text) ; todo when a is ref, render link
                   :tx (r/link ['.. '.. [:tx tx]] (dom/text tx)))))}))))))

(e/defn TxDetail [])
(e/defn EntityDetail [])
(e/defn EntityHistory [])
(e/defn DbStats [])
(e/defn RecentTx [])

(e/defn DatomicBrowser []
  (e/client
    (dom/h1 (dom/text "Datomic browser" " - " (pr-str r/route)))
    (dom/link (dom/props {:rel :stylesheet, :href "gridsheet-optional.css"}))
    (dom/div (dom/props {:class "user-gridsheet-demo"})
      (dom/div (dom/text "Nav: ")
        (r/link ['. []] (dom/text "home")) (dom/text " ")
        (r/link ['. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
        (r/link ['. [:recent-tx]] (dom/text "recent-tx")))
      (let [[page] r/route]
        (if-not page (r/ReplaceState! ['. [:attributes]])
          (r/pop
            (case page
              :attributes (Attributes)
              :attribute (AttributeDetail)
              :tx (TxDetail)
              :entity (e/amb (EntityDetail) (EntityHistory))
              :db-stats (DbStats)
              :recent-tx (RecentTx))))))))