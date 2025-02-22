(ns datomic-browser.datomic-browser3
  (:require clojure.core.protocols
            clojure.string
            [contrib.str :refer [pprint-str]]
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :refer [easy-attr]])
            #?(:clj dustingetz.datomic-contrib) ; datafy entity
            [dustingetz.entity-browser3 :refer [HfqlRoot *hfql-bindings Render]]
            dustingetz.identify
            #?(:clj dustingetz.mbrainz)
            electric-fiddle.fiddle-index
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]
            [hyperfiddle.router4 :as r]))

(e/declare conn)
(e/declare ^:dynamic db)

#?(:clj (tests (require '[clojure.datafy :refer [datafy nav]]
                 '[dustingetz.mbrainz :refer [test-db lennon]])))

#?(:clj (tests "insight: nav on dehydrated collection with nil key can be used to hydrate an object in context"
          #_(clojure.repl/doc nav) ; big idea: k is optional actually!
          ; nav can use k if it helps you enrich the object but you don't have to!
          ; G: I think it was a mistake for Rich to make nav look like get and get-in
          (def xs (with-meta [123 125 lennon] ; attach polymorphic context to the resultset not the element
                    {`clojure.core.protocols/nav ; polymorphic not by type but by meta
                     (fn [xs k v] (d/entity @test-db v))}))
          "nav can resolve a hydrated object from a dehydrated resultset"
          (nav xs nil lennon) := (d/entity @test-db lennon)))

#?(:clj (defn attributes [db]
          (with-meta
            (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            {`dustingetz.identify/-identify (fn [ctx v] v)
             #_#_`factory (partial d/entity db)
             `clojure.core.protocols/nav (fn [xs k v] (d/entity db v))})))

#?(:clj (tests
          (require '[dustingetz.hfql11 :refer [hfql-search-sort]])
          (time (count (->> (attributes @dustingetz.mbrainz/test-db) (hfql-search-sort {#'db @test-db} [:db/ident `(summarize-attr* ~'%) #_'*] "ref one")))) := 18
          (time (count (->> (attributes @dustingetz.mbrainz/test-db) (hfql-search-sort {#'db @test-db} [:db/ident `(summarize-attr* ~'%) #_'*] "sys")))) := 3))

(e/defn Attributes [] (e/server (attributes db)))

#?(:clj (defn datom->map [[e a v tx added]]
          (with-meta {:e e, :a a, :v v, :tx tx, :added added}
            {`dustingetz.identify/-identify (constantly e)})))

#?(:clj (defn aevt [db a] (->> (d/datoms db :aevt a) (sort-by :v) (map datom->map)))) ; todo inline

#?(:clj (tests
          (time (count (aevt @test-db :abstractRelease/name))) := 10180
          (datafy (first (aevt @test-db :abstractRelease/name)))
          := {:e 17592186061094, :a 79, :v "!Revolucion con Brasilia!", :tx 13194139549804, :added true}))

(e/defn AttributeDetail [a] (e/server (aevt db a)))
(e/defn DbStats [] (e/server (d/db-stats db)))
(e/defn EntityDetail [e] (e/server (d/entity db e)))

; TODO e a should be refs so we can render them as links
(e/defn TxDetail [e] (e/server (->> (d/tx-range (d/log conn) e (inc e))
                                 (eduction (mapcat :data) (map datom->map))))) ; really hydrate here? can we delay?

#?(:clj (defn summarize-attr* [?!a] #_[db]
          (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " ")))))

#?(:clj (defn attributes-count [{:keys [datoms attrs] :as m}]
          (->> (update-vals attrs :count)
            (into [] (map (fn [[k v]] {:key k, :count v})))
            (sort-by :count #(compare %2 %))
            vec)))

(e/defn AttributeCell [?e a v pull-expr]
  (Render ?e a (e/server (:db/ident (d/entity db v))) pull-expr))

(e/defn EntityTooltip [_?e _a v _pull-expr] ; questionable, oddly similar to hf/Render signature
  (e/server (pprint-str (e/server (d/pull db ['*] v)))))

(e/defn TxDetailValueTooltip [x col v pull-expr]
  (e/server
    (let [a (get x 'a) ; symbolic why
          [typ _ unique?] (easy-attr db a)]
      (cond
        (= :ref typ) (pprint-str (d/pull db ['*] v))
        (= :identity unique?) (pprint-str (d/pull db ['*] [a #_(:db/ident (d/entity db a)) v])) ; resolve lookup ref
        () nil))))

#?(:clj (defn entity-history [e] #_[db]
          (let [history (d/history db)]
            (eduction cat (map datom->map)
              [(d/datoms history :eavt (:db/id e e)) ; resolve both data and object repr, todo revisit
               (d/datoms history :vaet (:db/id e e))]))))

(e/defn EntityHistory [e] (e/server (with-bindings *hfql-bindings (entity-history e))))

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
                                     :hf/Render `AttributeCell
                                     :hf/Tooltip `EntityTooltip})
                      (with-meta 'v {:hf/Tooltip `TxDetailValueTooltip})]
           `EntityDetail [(with-meta 'db/id {:hf/Render `EntityDbidCell ; todo strengthen hfql links
                                             #_#_:hf/link `(EntityHistory ~'db/id)
                                             :hf/select `(EntityDetail ~'%)})
                          '*]

           `EntityHistory ['*]}))

(e/defn Index [_sitemap] ; todo infer non-partial routes from sitemap
  (e/client (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [[`Attributes]]] (dom/text "Attributes"))
    (r/link ['. [[`DbStats]]] (dom/text "DbStats"))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn DatomicBrowser3 [conn]
  (binding [electric-fiddle.fiddle-index/pages
            {`Attributes Attributes
             `AttributeDetail AttributeDetail
             `DbStats DbStats
             `TxDetail TxDetail
             `EntityDetail EntityDetail
             `EntityHistory EntityHistory}
            dustingetz.entity-browser3/whitelist
            {`AttributeCell AttributeCell
             `EntityTooltip EntityTooltip
             `TxDetailValueTooltip TxDetailValueTooltip
             `EntityDbidCell EntityDbidCell}
            conn conn
            db (e/server (ex/Offload-latch #(d/db conn)))] ; electric binding
    (binding [*hfql-bindings (e/server {(find-var `db) db})] ; clojure binding
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
