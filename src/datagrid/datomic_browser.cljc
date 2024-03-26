(ns datagrid.datomic-browser
  (:require [clojure.core.protocols :as ccp]
            [hyperfiddle.electric :as e]
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

#?(:clj
   (extend-type datomic.query.EntityMap
     ccp/Datafiable
     (datafy [^datomic.query.EntityMap entity]
       (with-meta
         (.cache entity) ; a map of already realized entries, don't force new entries
         {`ccp/nav
          (fn [map k v] ; nav on the entity
            (get entity k v))}))))

(e/def conn)
(e/def db)
(e/def schema)

(e/defn Schema [db] (e/server (schema/datomic->malli-schema (new (dx/schema> db)))))

;;; Queries

#?(:clj
   (defn query-attributes> [db pull-pattern]
     (->> (dx/attributes> db pull-pattern) ; pull based
       ;; (m/eduction (map #(datomic.api/entity db (:db/ident %)))) ; map to entity for lazy nav
       (m/reductions conj [])
       (m/relieve {}))))

(defn compare-nil-last [x y]
  (cond
    (and (nil? x) (nil? y)) 0
    (nil? x) 1
    (nil? y) -1
    :else (compare x y)))

(defn smart-compare [a b]
  (if (and (map? a) (map? b))
    (smart-compare (:db/ident a) (:db/ident b))
    (compare-nil-last a b)))

(defn smart-sort-by [column direction coll]
  (sort-by column
    (case direction
      (nil ::r/asc) smart-compare
      ::r/desc #(smart-compare %2 %1))
    coll))

(e/defn QueryAttributes [db pull-pattern]
  (e/server
    (let [[column direction] (e/client (first r/column-sort-spec))]
      (smart-sort-by (or column :db/ident) direction
        (new (query-attributes> db pull-pattern))))) )

(e/defn QueryAttributeDetails [db a]
  (e/server
    (new (->> (d/datoms> db {:index :aevt, :components [a]})
           (m/reductions conj [])
           (m/relieve {})))))

(e/defn QueryDbStats [db]
  (e/server
    (let [stats (new (e/task->cp (d/db-stats db)))]
      (cons [:datoms (:datoms stats)] (sort-by key (:attrs stats))))))

(e/defn QueryRecentTxs [db]
  (e/server
    (new (->> (d/datoms> db {:index :aevt, :components [:db/txInstant]})
           (m/reductions conj ())
           (m/relieve {})))))

(e/defn QueryEntityDetail [e]
  (e/server
    (assert e)
    (new (e/task->cp (d/pull db {:eid e :selector ['*] :compare compare})))))

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

;;; Progressive Enhancement

(e/defn RenderKeyword [props e a V]
  (e/server
    (let [v (V.)]
      (e/client
        (dom/text (if (= :db/ident a) v (name (or v ""))))))))

(defn target->link-path [base target v]
  (if (vector? target)
    (let [syms #{'. '.. '/}
          [path value] [(take-while syms target) (drop-while syms target)]]
      (conj (into [base] path) (conj (vec value) v)))
    [base [target v]]))

(e/defn RenderLink [target props e a V] ;; TODO move link directive to props, add MapProps
  (e/server
    (let [v (V.)]
      (e/client
        (router/link (target->link-path '.. target v) (dom/text v))))))

(e/defn RenderTxId [props e a V]
  (let [[[e a V]  [e⁻¹ a⁻¹ V⁻¹]] r/stack
        [_e _a _v tx _op]        (V⁻¹.)]
    (e/client
      (router/link ['.. [::tx tx]] (dom/text tx)))))

#?(:clj
   (defn human-friendly-identity-value [entity-like]
     (if (or (map? entity-like)
           (instance? datomic.query.EntityMap entity-like))
       (or (:db/ident entity-like) ; highest precedence
         ;; TODO pick a human friendly lookup ref based on a :db.unique/identity attr
         (:db/id entity-like)) ; lowest precedence
       entity-like)))

#?(:clj
   (defn human-friendly-identity [db entity-like]
     (cond (instance? datomic.query.EntityMap entity-like)
           (human-friendly-identity-value entity-like)

           (map? entity-like)
           (human-friendly-identity-value
             (datomic.api/entity db (human-friendly-identity-value entity-like)))

           :else entity-like)))

(e/defn Human-Friendly-Identity [V]
  (e/server (e/fn* [] (r/JoinValue. (human-friendly-identity db (V.))))))

(e/defn MapV [T F]
  (e/fn [props e a V]
    (F. props e a (T. V))))

(e/defn RenderRef [props e a V]
  (e/server
    (let [V (e/share V)
          v (V.)]
      (cond (keyword? v) (RenderKeyword. props e a V)
            :else  (r/DefaultRenderer. props e a V))) ;; TODO account for db/id and lookup refs
    ))

(e/def RenderSmartRef (MapV. Human-Friendly-Identity RenderRef))

(e/defn RenderRefLink [props e a V]
  (e/server
    (let [V (e/share V)
          v (V.)]
      (case a
        :db/id (e/client
                 (router/link ['.. [::entity v]]
                   (e/server
                     (RenderRef. props e a V))))
        (e/client
          (router/link ['.. [::attribute v]]
            (e/server
              (RenderRef. props e a V))))))))

(e/def RenderSmartRefLink (MapV. Human-Friendly-Identity RenderRefLink))

(e/defn RouterInput [props attribute]
  (e/client
    (r/RouterStorage. attribute
      (e/fn* [value]
        (r/RenderInput. props attribute value)))))

;;; Pages

(e/defn Attributes []
  (e/server
    (RouterInput. {::dom/placeholder ":release/name"} :db/ident)
    (r/RenderGrid. {::r/row-height-px 25
                    ::r/max-height-px "100%"
                    ::r/columns       [{::r/attribute :db/ident
                                        ::r/sortable true}
                                       {::r/attribute :db/valueType
                                        ::r/sortable true}
                                       {::r/attribute :db/cardinality
                                        ::r/sortable false}
                                       {::r/attribute :db/unique
                                        ::r/sortable true}
                                       {::r/attribute :db/isComponent
                                        ::r/sortable true}]}
      nil ; e
      nil ; a
      (e/fn* [] ;; V
        (r/InputFilter. :db/ident
          (QueryAttributes. db [:db/ident
                                {:db/valueType [:db/ident]}
                                {:db/cardinality [:db/ident]}
                                {:db/unique [:db/ident]}
                                :db/isComponent]))))))

(e/defn AttributeDetail [a]
  (e/client (dom/h1 (dom/text "Attribute detail: " a)))
  (e/server
    (RouterInput. {::dom/placeholder "Pour l’amour…"} :v)
    (binding [r/renderers (assoc r/renderers
                            :e (e/partial 5 RenderLink ::entity)
                            :tx (e/partial 5 RenderLink ::tx))]
      (r/RenderGrid. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::r/columns       [{::r/attribute :e}
                                         {::r/attribute :a}
                                         {::r/attribute :v}
                                         {::r/attribute :tx}]
                      ::dom/props       {:style {:grid-template-columns "1fr 1fr 1fr 1fr"}}}
        nil ; e
        nil ; a
        (e/fn* [] ; V
          (r/InputFilter. :v (QueryAttributeDetails. db a)))))))

(e/defn DbStats []
  (e/client (dom/h1 (dom/text "Db stats")))
  (e/server
    (RouterInput. {::dom/placeholder ":release/name"} ::r/key)
    (binding [r/RenderKey (e/partial 5 RenderLink ::attribute)]
      (r/RenderForm. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::dom/props {:style {:grid-template-columns "20rem auto"}}}
        nil ; e
        nil ; a
        (e/fn* [] ; V
          (r/InputFilter. first ::r/key
            (QueryDbStats. db)))))))

(e/defn RecentTx []
  (e/client (dom/h1 (dom/text "Recent Txs")))
  (e/server
    (RouterInput. {::dom/type "date"} :db/txInstant) ; FIXME This input should produce an #inst, today produces a string
    (binding [r/renderers (assoc r/renderers :db/id (e/partial 5 RenderLink ::tx))]
      (r/RenderGrid. {::r/row-height-px 25
                      ::r/max-height-px "100%"
                      ::r/columns       [{::r/attribute :db/id}
                                         {::r/attribute :db/txInstant}]
                      ::dom/props       {:style {:grid-template-columns "20rem auto"}}}
        nil ; e
        nil ; a
        (e/fn* [] ; V
          (r/InputFilter. compare :db/txInstant :db/txInstant
            (QueryRecentTxs. db)))))))

(e/defn MaybeRenderLink [target props e a V]
  (e/server
    (let [V (e/share V)
          v (V.)]
      (if (and (keyword? v) (schema/schema r/schema-registry v))
        (RenderLink. target props e a V)
        (RenderSmartRef. props e a V)))))

(e/defn EntityDetail [e] ; TODO render keys as attributes links
  (e/client
    (dom/h1 (dom/text "Entity detail: " e)) ; treeview on the entity
    (router/focus [`EntityDetail]
      (e/server
        (binding [r/Sequence (e/fn* [entity] (tree-seq-entity (new (dx/schema> db)) entity))
                  r/RenderKey (e/partial 5 MaybeRenderLink ['.. ::attribute])] ;; FIXME wrong layer due to router/focus
          (r/RenderForm. {::r/row-height-px 25
                          ::r/max-height-px "100%"}
            nil ; e
            nil ; a
            (e/fn* [] ; V
             (QueryEntityDetail. e) )))))))

;;; Entrypoint

(e/defn DatomicBrowser []
  (e/server
    (let [conn (e/offload #(d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))
          db   (new (e/task->cp (d/db conn)))]
      (binding [conn              conn
                db                db
                r/schema-registry (Schema. db)
                r/Render          r/SchemaRenderer
                r/renderers       (merge r/renderers {:db/ident RenderSmartRefLink
                                                      :db.type/ref RenderSmartRef})]
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
                  ::summary   (e/server (Attributes.))
                  ::attribute (e/server (AttributeDetail. x))
                  ;; ::tx (e/server (TxDetail. x))
                  ::entity
                  (do (e/server (EntityDetail. x))
                      #_(e/server (EntityHistory. x)))
                  ::db-stats  (e/server (DbStats.))
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
;; - [X] Factor out queries
;; - [X] Factor out common boilerplate
;; - [ ] Explore declarative gray inputs
;;   - [ ] How do they interact with queries?
;;   - [ ] Inject gray input storage mechanism
;;
;;
;;
;; (e/defn Schema [db] (e/server (datomic->malli-schema (new (dx/schema> db)))))


(comment
  (def _conn (datomic.api/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))
  (def _db   (datomic.api/db _conn))
  (prn (datomic.api/touch (datomic.api/entity _db 38)))
  (datomic.api/entity _db 38)
  (into {} (datomic.api/entity _db 38))
  (.cache (datomic.api/entity _db 38))
  (let [e (datomic.api/entity _db 38)]
    ;; (:db/doc e)
    (.cache e))
  (type (.cache (datomic.api/entity _db 38)))

  (ccp/datafy (first _V))
  (ccp/nav (ccp/datafy (first _V)) :v :not-found)
  (type (first _V))
  ccp/Datafiable
  )

(comment
  _schema
  (malli.registry/schema _schema :db/txInstant)
  (malli.registry/schema (schema/registry _r_schema_registry {:db/txInstant :string}) :db/txInstant)
  (malli.core/form (first (malli.core/children (malli.registry/schema _r_schema_registry :db/txInstant))))
  (malli.core/form (first (malli.core/children (malli.registry/schema (schema/registry {:db/txInstant [:schema {} :string]}) :db/txInstant))))
  )
