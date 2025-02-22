(ns datomic-browser.datomic-browser3
  (:require clojure.core.protocols
            clojure.string
            [contrib.data :refer [map-entry]]
            [contrib.debug :refer [dbg-ok]]
            contrib.str
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :refer [easy-attr]])
            #?(:clj [dustingetz.datomic-contrib :as dx]) ; datafy entity
            [dustingetz.entity-browser3 :as eb :refer [HfqlRoot *hfql-spec *hfql-bindings TableBlock2 TreeBlock Render]]
            #?(:clj [dustingetz.hfql11 :refer [hf-pull hf-pull3 hf-nav2]])
            [dustingetz.identify :refer [identify]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms4 :refer [Interpreter]]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            #?(:clj [markdown.core :as md])))

(e/declare conn)
(e/declare ^:dynamic db)

#?(:clj (defn attributes [db hfql-spec search]
          (with-meta
            (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            {`dustingetz.identify/-identify (fn [ctx v] v)
             #_#_`factory (partial d/entity db)
             `clojure.core.protocols/nav (fn [xs k v] (d/entity db v))})))

(comment
  (require '[clojure.datafy :refer [nav]])
  (clojure.repl/doc nav)
  (def xs (with-meta [123 125 lennon] ; polymorphic not by type but by meta
            {`clojure.core.protocols/nav (fn [xs k v] (d/entity @test-db v))}))
  ; k is optional actually, use it if it helps you enrich the object but you don't have to
  ; G: I think it was a mistake for Rich to make nav look like get and get-in
  (nav xs nil lennon) := (d/entity @test-db lennon)
  )

(comment
  (require '[dustingetz.mbrainz :refer [test-db]])
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "ref one"))) := 18
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "sys"))) := 3)

(e/defn Attributes []
  (e/server (attributes db *hfql-spec "")))

#?(:clj (defn datom->map [[e a v tx added]]
          (with-meta
            {:e e, :a a, :v v, :tx tx, :added added}
            {`dustingetz.identify/-identify (constantly e)})))
#?(:clj (defn aevt [db a] ; todo inline
          (->> (d/datoms db :aevt a) (sort-by :v) (map datom->map))))

(comment
  (time (count (aevt @test-db :abstractRelease/name))) := 10180
  (clojure.datafy/datafy (first (aevt @test-db :abstractRelease/name))))

(e/defn AttributeDetail [a]
  (e/server (aevt db a)))

(e/defn DbStats []
  (e/server (d/db-stats db)))

(e/defn EntityDetail [e]
  (e/server (d/entity db e)))

;; TODO mismacth, Datoms are vector-like
;; TODO e a should be refs so we can render them as links
#?(:clj (defn tx-detail [conn e hfql-spec search]
          (->> (d/tx-range (d/log conn) e (inc e))
            (eduction (mapcat :data)
              (map (fn [[e a v tx op]]
                     (let [m {:e e, :a a, :v v, :tx tx, :op op}] ; FIXME use datom->map
                       (with-meta ((hf-pull hfql-spec) {'% m})
                         {`dustingetz.identify/-identify (constantly e)}))))
              (filter #(contrib.str/includes-str? % search))))))

(e/defn TxDetail [e]
  (e/server (tx-detail conn e *hfql-spec "")))

#?(:clj (defn summarize-attr* [?!a] ; db comes from hfql dynamic binding conveyance
          (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " ")))))

#?(:clj (defn attributes-count [{:keys [datoms attrs] :as m}]
          (->> (update-vals attrs :count)
            (into [] (map (fn [[k v]] {:key k, :count v})))
            (sort-by :count #(compare %2 %))
            vec)))

(e/defn Attribute [?e a v pull-expr]
  (Render ?e a (e/server (:db/ident (d/entity db v))) pull-expr))

(e/defn EntityTooltip [_?e _a v _pull-expr] ; questionable, oddly similar to hf/Render signature
  (e/server (contrib.str/pprint-str (e/server (d/pull db ['*] v)))))

#?(:clj (defn entity-history [e] #_[db]
          (let [history (d/history db)]
            (eduction cat (map datom->map)
              [(d/datoms history :eavt (:db/id e e)) ; resolve both data and object repr, todo revisit
               (d/datoms history :vaet (:db/id e e))]))))

(e/defn EntityHistory [e]
  (e/server (with-bindings {(find-var `db) db}
              (entity-history e)))) ; dynamic db

(e/defn EntityDbidCell [?e a v pull-expr] #_(Render ?e a v pull-expr)
  (dom/text v " ") (r/link ['.. [`(EntityHistory ~v)]] (dom/text "entity history")))

#?(:clj (def sitemap
          {`Attributes [(with-meta 'db/ident {:hf/link `(AttributeDetail ~'db/ident)
                                              :hf/Tooltip `EntityTooltip})
                        `(summarize-attr* ~'%)
                        :db/doc
                        '*]
           `DbStats [:datoms `(attributes-count ~'%)] ; TODO render shorter name for `(attributes-count %)`
           ; TODO custom key/value renderers - conflict with treelister
           `AttributeDetail [(with-meta 'e {:hf/link `(EntityDetail ~'e)
                                            :hf/Tooltip `EntityTooltip})
                             :v
                             (with-meta 'tx {:hf/link `(TxDetail ~'tx)
                                             :hf/Tooltip `EntityTooltip})
                             '*
                             #_:added]
           `TxDetail [(with-meta 'e {:hf/link `(EntityDetail ~'e)
                                     :hf/Tooltip `EntityTooltip})
                      (with-meta 'a {:hf/link `(AttributeDetail ~'a)
                                     :hf/Render `Attribute
                                     :hf/Tooltip `EntityTooltip})
                      :v]
           `EntityDetail [(with-meta 'db/id {:hf/Render `EntityDbidCell ; todo strengthen hfql links
                                             #_#_:hf/link `(EntityHistory ~'db/id)
                                             :hf/select `(EntityDetail ~'%)})
                          '*]

           `EntityHistory ['*]
           `SiteMap ['*]}))

(e/defn SiteMap []
  (e/server sitemap))

(e/defn Index [_sitemap]
  ;; TODO auto-derive from sitemap, only for top-level, non-partial links.
  ;;      or provide a picker to fulfill missing args
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [[`Attributes]]] (dom/text "Attributes"))
    (r/link ['. [[`DbStats]]] (dom/text "DbStats"))
    (r/link ['. [[`SiteMap]]] (dom/text "SiteMap"))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn DatomicBrowser3 [conn]
  (binding [pages {`Attributes Attributes
                   `AttributeDetail AttributeDetail
                   `DbStats DbStats
                   `TxDetail TxDetail
                   `EntityDetail EntityDetail
                   `SiteMap SiteMap
                   `EntityHistory EntityHistory}
            eb/whitelist {`Attribute Attribute
                          `EntityTooltip EntityTooltip
                          `EntityDbidCell EntityDbidCell}
            conn conn
            db (e/server (ex/Offload-latch #(d/db conn)))]
    (binding [eb/*hfql-bindings (e/server {(find-var `db) db})]
      (dom/style (dom/text css))
      (let [sitemap (e/server sitemap)]
        (Index sitemap)
        (HfqlRoot sitemap :default `[[Attributes]])))))

(def css "
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser fieldset { position: relative; height: 25em; }
:where(.Browser fieldset.entity)          table { grid-template-columns: 15em auto; }
.Browser fieldset.entity-children table { grid-template-columns: repeat(var(--col-count), 1fr); }

/* table cell tooltips */
.Browser td {position: relative;}
.Browser .dustingetz-tooltip >       span { visibility: hidden; }
.Browser .dustingetz-tooltip:hover > span { visibility: visible; pointer-events: none; }
.Browser .dustingetz-tooltip > span {
  position: absolute; top: 20px; left: 10px; z-index: 2; /* interaction with row selection z=1 */
  margin-top: 4px; padding: 4px; font-size: smaller;
  box-shadow: 0 0 .5rem gray; border: 1px whitesmoke solid; border-radius: 3px; background-color: white; }
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser .-datomic-browser-dbob-db-stats table { grid-template-columns: 36ch auto;}
")
