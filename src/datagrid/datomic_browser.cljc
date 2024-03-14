(ns datagrid.datomic-browser
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]
            [contrib.datomic-m #?(:clj :as :cljs :as-alias) d]
            #?(:clj [contrib.datomic-contrib :as dx])
            ;; #?(:clj [datomic.api :as d])
            [datagrid.datafy-renderer :as r]
            [missionary.core :as m]
            [hyperfiddle.electric-css :as css]))

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

(e/defn Attributes []
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/JoinValue       e/Identity
              r/Nav             Nav
              r/schema-registry (r/registry {::attributes    [:sequential {:cardinality :many} :any]
                                             :db/isComponent :boolean} ;; TODO fixme checkbox doesn't uncheck
                                  )
              r/renderers       (assoc r/renderers
                                  :db/ident RenderAttribute
                                  :db/valueType RenderKeyword
                                  :db/cardinality RenderKeyword
                                  :db/unique RenderKeyword)]
      (let [columns [:db/ident :db/valueType :db/cardinality :db/unique :db/isComponent]]
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
              r/schema-registry (r/registry {::datoms    [:sequential {:cardinality :many} :any]
                                             :db/isComponent :boolean}
                                  )
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
              r/schema-registry (r/registry {::stats    [:sequential {:cardinality :many} :any]
                                             :db/isComponent :boolean}
                                  )
              r/renderers       (assoc r/renderers
                                  ::k RenderKey
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
              r/schema-registry (r/registry {::txs [:sequential {:cardinality :many} :any]
                                             :db/txInstant :string})
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


(e/defn DatomicBrowser []
  (e/server
    (let [conn (e/offload #(d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))
          db   (new (e/task->cp (d/db conn)))]
      (binding [conn   conn
                db     db
                schema (new (dx/schema> db))]
                                        ; index-route (or (seq args) [::summary])
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
                  ;; ::entity
                  #_(do (e/server (EntityDetail. x))
                        (e/server (EntityHistory. x)))
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
