(ns datagrid.datomic-browser
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]
            [contrib.datomic-m #?(:clj :as :cljs :as-alias) d]
            #?(:clj [contrib.datomic-contrib :as dx])
            [datagrid.datafy-renderer :as r]
            [datagrid.schema :as schema]
            [missionary.core :as m]
            [hyperfiddle.electric-css :as css]
            [malli.util]
            [malli.core]))

(e/def conn)
(e/def db)
(e/def schema)

(e/defn RenderAttribute [props e a V]
  (e/server
    (let [v (V.)]
      (e/client
        (router/link ['.. [::attribute v]] (dom/text v))))))

(e/defn RenderKeyword [props e a V]
  (e/server
    (when-let [v (V.)]
      (e/client
        (dom/text (name v))))))

#?(:clj
   (defn query-attributes> [db cols]
     (->> (dx/attributes> db cols)
       (m/reductions conj [])
       (m/relieve {}))))

;; Either call d/entity when query-attributes> retruns a list of refs
;; Or prefetch with a pull pattern and re-traverse the result with nav

(e/defn Nav [v a]
  (e/server
    (cond
      (map? v)
      (cond
        (:db/id v)    (get (new (e/task->cp (d/entity db (:db/id v)))) a) ;; TODO throws pending on nav, messes with the grid
        (:db/ident v) (get (new (e/task->cp (d/entity db (:db/ident v)))) a)
        :else         (get v a))
      (instance? datomic.query.EntityMap v) (get v a)
      :else                                 nil)))

;; (e/def Nav r/Nav)

(e/defn Attributes []
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/JoinValue       e/Identity
              r/Nav             Nav
              r/schema-registry (schema/registry r/schema-registry
                                  {::attributes [:any {::schema/cardinality ::schema/many}]})
              r/renderers       (assoc r/renderers
                                  :db/ident RenderAttribute
                                  :db/valueType RenderKeyword
                                  :db/cardinality RenderKeyword
                                  :db/unique RenderKeyword)]
      (let [columns [:db/ident {:db/valueType [:db/ident]} #_#_#_:db/cardinality :db/unique :db/isComponent]]
        (r/PushEAV. nil ::attributes (e/fn* [] (sort-by :db/ident (new (query-attributes> db columns))))
          (e/fn* [e a V]
            (r/Render. {::r/row-height-px 25
                        ::r/max-height-px "100%"
                        ::r/columns       [{::r/attribute :db/ident}
                                           {::r/attribute :db/valueType}
                                           {::r/attribute :db/cardinality}
                                           {::r/attribute :db/unique}
                                           {::r/attribute :db/isComponent}]}
              e a V)))))))

(e/defn RenderLink [target props e a V]
  (e/server
    (let [v (V.)]
      (e/client
        (router/link ['.. [target v]] (dom/text v))))))

(e/defn AttributeDetail [a]
  (e/client (dom/h1 (dom/text "Attribute detail: " a)))
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/JoinValue       e/Identity
              r/Nav             (e/fn* [v a] (get v a))
              r/schema-registry (schema/registry r/schema-registry {::datoms [:any {::schema/cardinality ::schema/many}]})
              r/renderers       (assoc r/renderers
                                  :e (e/partial 5 RenderLink ::entity)
                                  :tx (e/partial 5 RenderLink ::tx))]
      (r/PushEAV. nil ::datoms (e/fn* [] (new (->> (d/datoms> db {:index :aevt, :components [a]})
                                                (m/reductions conj [])
                                                (m/relieve {}))))
        (e/fn* [e a V]
          (r/Render. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::r/columns       [{::r/attribute :e}
                                         {::r/attribute :a}
                                         {::r/attribute :v}
                                         {::r/attribute :tx}]
                      ::dom/props {:style {:grid-template-columns "1fr 1fr 1fr 1fr"}}}
            e a V))))))

(e/defn RenderKey [props e a V]
  (e/server
    (let [v (V.)]
      (e/client
        (dom/text (pr-str v))))))

(e/defn RenderValue [props e a V]
  (e/server
    (let [v (V.)]
      (when-not (= ::attrs v)
        (e/client
          (dom/text (pr-str v)))))))

(e/defn DbStats []
  (e/client (dom/h1 (dom/text "Db stats")))
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/JoinValue       e/Identity
              r/Nav             (e/fn* [v a] (get v a))
              r/schema-registry (schema/registry r/schema-registry {::stats [:any {::schema/cardinality ::schema/many}]})
              r/renderers       (assoc r/renderers
                                  ::k r/RenderFormKey
                                  ::v RenderValue)]
      (r/PushEAV. nil ::stats (e/fn* []
                                (let [stats (new (e/task->cp (d/db-stats db)))]
                                  (map (fn [[k v]] {::k k, ::v v})
                                    (cons [:datoms (:datoms stats)] (sort-by key (:attrs stats))))))
        (e/fn* [e a V]
          (r/Render. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::r/columns       [{::r/attribute ::k}
                                         {::r/attribute ::v}]
                      ::dom/props {:style {:grid-template-columns "20rem auto"}}}
            e a V))))))

(e/defn RenderTxId [props e a V]
  (let [[[e a V]  [e⁻¹ a⁻¹ V⁻¹]] r/stack
        [_e _a _v tx _op]        (V⁻¹.)]
    (e/client
      (router/link ['.. [::tx tx]] (dom/text tx)))))

(e/defn RecentTx []
  (e/client (dom/h1 (dom/text "Recent Txs")))
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/JoinValue       e/Identity
              r/Nav             (e/fn* [[e a v tx op] a]
                                  (case a
                                    :db/id tx
                                    :db/txInstant v))
              r/schema-registry (let [s (schema/registry r/schema-registry {::txs [:any {::schema/cardinality ::schema/many}]})]
                                  (def _r_schema_registry r/schema-registry)
                                  (def _schema s)
                                  s)
              r/renderers       (assoc r/renderers
                                  :db/id (e/partial 5 RenderLink ::tx))]
      (r/PushEAV. nil ::txs (e/fn* []
                              (new (->> (d/datoms> db {:index :aevt, :components [:db/txInstant]})
                                       (m/reductions conj ())
                                       (m/relieve {}))))
        (e/fn* [e a V]
          (r/Render. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::r/columns       [{::r/attribute :db/id}
                                         {::r/attribute :db/txInstant}]
                      ::dom/props       {:style {:grid-template-columns "20rem auto"}}}
            e a V)))))
    )

(comment
  _schema
  (malli.registry/schema _schema :db/txInstant)
  (malli.registry/schema (schema/registry _r_schema_registry {:db/txInstant :string}) :db/txInstant)
  (malli.core/form (first (malli.core/children (malli.registry/schema _r_schema_registry :db/txInstant))))
  (malli.core/form (first (malli.core/children (malli.registry/schema (schema/registry {:db/txInstant [:schema {} :string]}) :db/txInstant))))
  )

#?(:clj
   (defn tree-seq-entity
     ([schema entity] (tree-seq-entity schema entity 0))
     ([schema entity rank]
      (->> (seq entity)
        (mapcat (fn [[k v :as entry]]
               (if-let [children (dx/entity-tree-entry-children schema entry)]
                 (into [{::r/key k, ::r/value nil ::r/rank rank}]
                   (tree-seq-entity schema children (inc rank)))
                 [{::r/key k, ::r/value v, ::r/rank rank}])))))))

(e/defn EntityDetail [e]
  (assert e)
  (e/client
    (dom/h1 (dom/text "Entity detail: " e)) ; treeview on the entity
    (router/focus [`EntityDetail]
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/JoinValue       e/Identity
                  r/Nav             (e/fn* [v a] (get v a))
                  r/renderers       (assoc r/renderers ::entity r/RenderForm)
                  r/Sequence        (e/fn* [entity]
                                      (tree-seq-entity (new (dx/schema> db)) entity))]
          (r/PushEAV. nil ::entity (e/fn* [] (new (e/task->cp (d/pull db {:eid e :selector ['*] :compare compare}))))
            (e/fn* [e a V]
              (r/Render. {::r/row-height-px 25
                          ::r/max-height-px "100%"}
                e a V)))))
      )))

(e/defn Schema [db] (e/server (schema/datomic->malli-schema (new (dx/schema> db)))))

(e/defn DatomicBrowser []
  (e/server
    (let [conn (e/offload #(d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))
          db   (new (e/task->cp (d/db conn)))]
      (binding [conn   conn
                db     db
                r/schema-registry (Schema. db)]
        ;; index-route (or (seq args) [::summary])
        (e/client
          (dom/props {:style {:height         "100vh"
                              :padding-bottom 0
                              :box-sizing     :border-box
                              :margin         0
                              :display        :flex
                              :flex-direction :column}
                      :class [(css/scoped-style
                                (css/rule ".virtual-scroll" {:flex 1}))]})
          (dom/h1 (dom/text "Datomic browser"))
          (dom/div (dom/text "Nav: ")
                   (router/link [[::summary]] (dom/text "home")) (dom/text " ")
                   (router/link [[::db-stats]] (dom/text "db-stats")) (dom/text " ")
                   (router/link [[::recent-tx]] (dom/text "recent-tx")))
          (let [[page x :as route] (ffirst router/route)]
            (if-not page
              (router/Navigate!. [[::summary]])
              (router/focus [route]
                (case page
                  ::summary (e/server (Attributes.))
                  ::attribute (e/server (AttributeDetail. x))
                  ;; ::tx (e/server (TxDetail. x))
                  ::entity
                  (do (e/server (EntityDetail. x))
                        #_(e/server (EntityHistory. x)))
                  ::db-stats (e/server (DbStats.))
                  ::recent-tx (e/server (RecentTx.))
                  (e/client (dom/text "no matching route: " (pr-str page))))))))))))

;; * How to run
;;   1. Start datomic - instructions in =electric-fiddle/src/datomic_browser/Readme.md=
;;   2. Run this fiddle with `clj -A:dev:local-deps:datagrid`
;;   3. Navigate to http://localhost:8080
;;
;; * Learnings
;; ** Throwing Pending in Nav messes with the grid rendering
;;    Cells content unmounts and remounts on scroll - not sure why yet
;; ** Different structures to Nav on (entity vs datom vs arbitrary map)
;;    Had to redefine ~datafy~ to ~identity~ because:
;;    - we only Nav on clojure data structures and datomic entities
;;    - entities do not need to be datafied, just use the PersistentMap interface
;; ** Entrypoint requires boilerplate (e.g. ::txs, ::attributes, ::stats)
;;    We have a schema with a fake card-n attribute just for the entrypoint
;; ** e/partial could be simplified
;;    Now that we have e/apply, e/partial do not need the arity count argument
;; ** Narrow columns do not horizontally size correctly (e.g. :e :a :v :tx)
;; ** Not obvious how to customize form fields rendering
;;    i.e `:db/id 1234` 1234 should render as a link, but attribute at point is ::value (not :db/id)
;;    See Format-entity
;;
;; * TODO
;; - [X] derive malli schema from datomic schema (queries)
;; - [X] Add tooltips on attributes
;; - [ ] Form renderer is in charge of rendering keys and values
;;   - [ ] Passes through to user-defined renderers
;; - [ ] Factor out queries
;; - [ ] Factor out common boilerplate
;; - [ ] Explore declarative gray inputs
;;   - [ ] How do they interact with queries?
;;   - [ ] Inject gray input storage mechanism
;;
;;
;;
;; (e/defn Schema [db] (e/server (datomic->malli-schema (new (dx/schema> db)))))


