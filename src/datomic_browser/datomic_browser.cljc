(ns datomic-browser.datomic-browser
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [contrib.data :refer [treelister clamp-left]]
            [contrib.str :refer [any-matches?]]
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :refer
                     [attributes-stream ident! entity-history-datoms easy-attr
                      is-attr? seq-consumer flatten-nested]])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input! Form! Checkbox]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router3 :as r]
            [missionary.core :as m]))

(e/defn TableScroll [?xs! Row]
  (e/server
    (dom/div (dom/props {:class "Viewport"})
      (let [record-count (count ?xs!)
            row-height 24
            [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [i (IndexRing limit offset)]
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              (Row (nth (vec ?xs!) i nil)))))
        (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                    (* row-height (- record-count limit)) 0) "px")}}))))))

(e/declare conn)
(e/declare db)

(e/defn Attributes []
  #_(r/focus [0]) ; search
  (dom/fieldset (dom/legend (dom/text "Attributes"))
    (TableScroll
      (e/server
        (->> (attributes-stream (doto db (prn 'db))
               [:db/ident :db/unique :db/isComponent
                {:db/valueType [:db/ident]}
                {:db/cardinality [:db/ident]}
                #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs])
          (m/reduce conj []) e/Task (sort-by :db/ident)))
      (e/fn [x]
        (dom/td (let [v (:db/ident x)] (r/link ['.. [:attribute v]] (dom/text v))))
        (dom/td (some-> x :db/valueType :db/ident name dom/text))
        (dom/td (some-> x :db/cardinality :db/ident name dom/text))
        (dom/td (some-> x :db/unique :db/ident name dom/text))
        (dom/td (some-> x :db/isComponent :db/ident name dom/text))))))

(e/defn AttributeDetail []
  (let [[a _] r/route]
    #_(r/focus [1]) ; search
    (when a ; router glitch
      (dom/fieldset (dom/legend (dom/text "Attribute index: " (pr-str a)))
        (e/server
          (TableScroll
            (->> (seq-consumer (d/datoms db :aevt a))
              (m/reduce conj []) e/Task (sort-by :v))
            (e/fn [[e _ v tx op]] ; possible destr glitch
              (dom/td (r/link ['.. [:entity e]] (dom/text e)))
              (dom/td (dom/text (pr-str a)))
              (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
              (dom/td (r/link ['.. [:tx-detail tx]] (dom/text tx))))))))))

(e/defn TxDetail []
  (let [[e _] r/route]
    #_(r/focus [1]) ; search
    (when e ; router glitch
      (dom/fieldset (dom/legend (dom/text "Tx detail: " e))
        (e/server
          (TableScroll
            (->> (seq-consumer (d/tx-range (d/log conn) e (inc e))) ; global
              (m/eduction (map :data) cat) (m/reduce conj []) e/Task)
            (e/fn [[e aa v tx op]] ; possible destr glitch
              (dom/td #_(let [e (e/server (e/Task (ident! db e)))]) (r/link ['.. [:entity e]] (dom/text e)))
              (dom/td (let [aa (e/server (e/Task (ident! db aa)))] (r/link ['.. [:attribute aa]] (dom/text aa))))
              (dom/td (dom/text (pr-str v))) ; todo if a is ref, present link
              (dom/td (r/link ['.. [:tx-detail tx]] (dom/text tx))))))))))

(e/defn Format-entity [{:keys [path name value] :as ?row}]
  (e/server ; keep vals on server, row can contain refs
    (let [k name v value]
      (when ?row
        (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
          (cond
            (= :db/id k) (dom/text k) ; :db/id is our schema extension, can't nav to it
            (is-attr? db k) (r/link ['.. [:attribute k]] (dom/text k))
            () (dom/text (str k)))) ; str is needed for Long db/id, why?
        (dom/td
          (if-not (coll? v) ; don't render card :many intermediate row
            (let [[valueType cardinality] (easy-attr db k)]
              (cond
                (= :db/id k) (r/link ['.. [:entity v]] (dom/text v))
                (= :ref valueType) (r/link ['.. [:entity v]] (dom/text v))
                (= :string valueType) (dom/text v) #_(Form! (Input! k v) :commit (fn [] []) :show-buttons :smart)
                () (dom/text (str v))))))))))

(e/defn EntityDetail []
  (let [[e _] r/route]
    #_(r/focus [1]) ; search
    (when e ; glitch
      (dom/fieldset (dom/legend (dom/text "Entity detail: " e))
        (e/server
          (TableScroll
            (flatten-nested (e/Task (m/via m/blk (d/pull db ['*] e))))
            Format-entity #_{::dom/class "Viewport entity-detail"}))))))

(e/defn Format-history-row [[e aa v tx op :as ?row]]
  (when ?row ; glitch
    (dom/td (e/client (dom/text (name (case op true :db/add false :db/retract (e/amb))))))
    (dom/td (e/client (r/link ['.. [:entity e]] (dom/text e)))) ; link two levels up because we are under EntityHistory's scope
    (dom/td (if (some? aa)
              (let [ident (:db/ident (e/Task (m/via m/blk (d/pull db [:db/ident] aa))))]
                (dom/text (pr-str ident)))))
    (dom/td (some-> v pr-str dom/text))
    (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
    (dom/td (let [x (:db/txInstant (e/Task (m/via m/blk (d/pull db [:db/txInstant] tx))))]
              (dom/text (e/client (pr-str x)))))))

(e/defn EntityHistory []
  (let [[e _] r/route]
    #_(r/focus [1]) ; search
    (when e ; router glitch
      (dom/fieldset (dom/legend (dom/text "Entity history: " e))
        (TableScroll
          (e/server (->> (entity-history-datoms db e) (m/reduce conj []) e/Task))
          Format-history-row
          #_{:columns [::e ::a ::op ::v ::tx-instant ::tx]
             ::dom/class "Viewport entity-history"})))))

(e/defn DbStats []
  #_(r/focus [0]) ; search
  (dom/fieldset (dom/legend (dom/text "Db stats"))
    (TableScroll
      (e/server
        #_(flatten-nested (e/Task (m/via m/blk (d/db-stats db)))) ; need more control to inline short maps
        (seq ((treelister (fn [[k v]] (condp = k :attrs (into (sorted-map) v) nil)) any-matches?
                (e/Task (m/via m/blk (d/db-stats db)))) "")))
      (e/fn [[tab [k v]]] ; thead: [::k ::v]
        (dom/td (dom/text (pr-str k)) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
        (dom/td (cond
                  (= k :attrs) nil ; print children instead
                  () (dom/text (pr-str v))))))))

(comment
  {:datoms 800958,
   :attrs
   {:release/script {:count 11435},
    :label/type {:count 870}
    ... ...}})

(e/defn RecentTx []
  #_(r/focus [0]) ; search
  (dom/fieldset (dom/legend (dom/text "Recent Txs"))
    (e/server
      (TableScroll
        (->> (d/datoms db :aevt :db/txInstant) seq-consumer (m/reduce conj ()) e/Task)
        (e/fn [[e _ v tx op :as record]]
          ; columns [:db/id :db/txInstant]
          (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
          (dom/td (dom/text (e/client (pr-str v)) #_(e/client (.toLocaleDateString v)))))))))

(e/defn Nav []
  (dom/div (dom/props {:class "nav"})
    (dom/text "Nav: ")
    (r/link ['.. [:attributes]] (dom/text "attributes")) (dom/text " ")
    (r/link ['.. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
    (r/link ['.. [:recent-tx]] (dom/text "recent-tx"))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn Page []
  (dom/style (dom/text css))
  (dom/props {:class "DatomicBrowser Explorer"})
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:attributes]]))
    (dom/props {:class page})
    (r/pop
      (Nav)
      (case page
        :attributes (Attributes)
        :attribute (AttributeDetail)
        :tx-detail (TxDetail)
        :entity (e/amb (EntityDetail) (EntityHistory))
        :db-stats (DbStats)
        :recent-tx (RecentTx)
        (e/amb))
      (dom/footer (dom/p (dom/text "Stability note: Electric v3 is not quite 100% stable and this demo
      pushes Electric very hard. If the page breaks please kindly refresh. As you can see, we're very close!"))))))

(e/defn DatomicBrowser [conn]
  (e/client
    (let [fail true #_(dom/div (Checkbox true :label "failure"))
          edits (bindx [conn conn, db (e/server (check (e/Task (m/via m/blk (d/db conn)))))]
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
/* Scroll machinery */
.Explorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.Explorer table { display: grid; }
.Explorer table tr { display: contents; visibility: var(--visibility); }
.Explorer table td { grid-row: var(--order); }
.Explorer div.Viewport { height: 100%; }

/* Userland layout */
.Explorer fieldset { position:fixed; top:3em; bottom:5em; left:0; right:0; }
.Explorer footer { position: fixed; bottom: 0; max-width: 45em; }
.Explorer table { grid-template-columns: 20em auto; }

/* Cosmetic */
.Explorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.Explorer legend { margin-left: 1em; font-size: larger; }
.Explorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.Explorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.Explorer table tr:hover td { background-color: #ddd; }

/* Progressive enhancement */
.Explorer .nav { margin: 0; }
.Explorer.entity fieldset:nth-of-type(1) { top:3em; bottom:66vh; left:0; right:0; }
.Explorer.entity fieldset:nth-of-type(2) { top:34vh; bottom:5em; left:0; right:0; }
.Explorer.entity fieldset:nth-of-type(1) table { grid-template-columns: 15em auto; }
.Explorer.entity fieldset:nth-of-type(2) table { grid-template-columns: 5em 10em 15em auto 10em 9em; }
.Explorer.attributes table { grid-template-columns: auto 6em 4em 4em 4em; }
.Explorer.attribute table { grid-template-columns: 15em 15em calc(100% - 15em - 15em - 9em) 9em; }
.Explorer.tx-detail table { grid-template-columns: 15em 15em calc(100% - 15em - 15em - 9em) 9em; }
.Explorer.recent-tx table { grid-template-columns: 10em auto; }
.Explorer.db-stats table { grid-template-columns: 20em auto; }
")