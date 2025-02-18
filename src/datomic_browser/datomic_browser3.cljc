(ns datomic-browser.datomic-browser3
  (:require clojure.string
            [contrib.data :refer [map-entry unqualify]]
            [contrib.debug :refer [dbg-ok]]
            contrib.str
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :refer [easy-attr]])
            #?(:clj [dustingetz.datomic-contrib :as dx]) ; datafy entity
            [dustingetz.easy-table :refer [Load-css]]
            [dustingetz.entity-browser3 :as eb :refer [HfqlRoot *hfql-spec TableBlock TableBlock2 TreeBlock Render]]
            [dustingetz.identify :refer [identify]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages NotFoundPage]]
            [clojure.datafy :refer [datafy nav]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms4 :refer [Interpreter]]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            #?(:clj [dustingetz.hfql11 :refer [hf-pull hf-pull3 hf-nav2]])
            #?(:clj [markdown.core :as md])))

(e/declare conn)
(e/declare db)

;; ----------------------------------------------------------------------

#?(:clj (defmulti title (fn [m] (some-> m meta :clojure.datafy/class))))
#?(:clj (defmethod title :default [m]
          (or (some-> m meta :clojure.datafy/obj identify)
            (some-> m meta :clojure.datafy/class str))))

(e/defn MarkdownBlock [field-name kv _selected & _]
  (dom/fieldset
    (dom/legend (dom/text (e/server (pr-str (key kv)))))
    (dom/div
      (set! (.-innerHTML dom/node) (e/server (md/md-to-html-string (val kv)))))))

(defn infer-block-type [x]
  ;; (prn "infer-block-type" (type x) x)
  (cond
    (or (set? x) ; align with explorer-seq which indexes sets
      (sequential? x)) :table ; datafy does not alter cardinality, do not need to call it
    (string? x) :string
    (map? (datafy x)) :tree ; fixme gross, how to detect scalars? Can we not emit selection on scalars instead?
    () :scalar))

(e/defn Block [kv locus]
  ;; (e/server (prn 'Block {:locus locus, :kv kv}))
  (e/client
    (let [x (e/server #_datafy (val kv))]
      (when-some [F (e/server (case (infer-block-type x) :tree TreeBlock :table TableBlock2 :string MarkdownBlock :scalar nil nil))]
        (Interpreter {::select (e/fn [path] (r/Navigate! ['. (if path [path] [])])
                                 [:hyperfiddle.electric-forms4/ok])}
          (F ::select kv locus *hfql-spec))))))

(defn- id->index [id xs!]
  (first (eduction (map-indexed vector)
           (keep (fn [[i v]] (when (= id (identify v)) i)))
           (take 1)
           xs!)))

(e/defn BrowsePath [kv]
  (e/client
    (let [locus (first r/route)]
      (Block kv locus)
      (when (some? locus)
        (r/pop
          (e/for [locus (e/diff-by identity (e/as-vec locus))] ; don't reuse DOM/IO frames across different objects
            (let [kv (e/server #_(ex/Offload-reset (fn []))
                               (case (infer-block-type (val kv))
                                 :table (let [value (vec (val kv))
                                              index (id->index (first locus) (datafy value))]
                                          (map-entry locus (hf-nav2 value index)))
                                 (map-entry locus (reduce hf-nav2 (val kv) locus))))]
              ;; stacked views should get '*. First `Block` renders above
              (binding [*hfql-spec (e/server ['*])]
                (BrowsePath kv)))))))))

#?(:clj (defn attributes [db hfql-spec search]
          (->> (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            (eduction
              (map #(d/entity db %))
              (map (fn [!e] [!e (hf-pull3 hfql-spec !e)])) ; pull everything for search
              (filter #(contrib.str/includes-str? (nth % 1) search)) ; search all pulled cols
              (map first)) ; unpull so we can datafy entity in view to infer cols
            sequence
            (sort-by (first hfql-spec)))))

(comment
  (require '[dustingetz.mbrainz :refer [test-db]])
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "ref one"))) := 18
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "sys"))) := 3)

(e/defn Attributes []
  (r/pop
    (BrowsePath (e/server (map-entry `Attributes (attributes db *hfql-spec ""))))))

#?(:clj (defn aevt [a]
          (let [db @dustingetz.mbrainz/test-db]
            (->> (d/datoms db :aevt a) (sort-by :v) (map (fn [[e a v tx added]]
                                                           (with-meta
                                                             {:e e, :a a, :v v, :tx tx, :added added}
                                                             {`dustingetz.identify/-identify (constantly e)})))))))

(comment
  (time (count (aevt :abstractRelease/name))) := 10180
  (clojure.datafy/datafy (first (aevt :abstractRelease/name))))

(e/defn AttributeDetail [a]
  (r/pop
    (BrowsePath (e/server (map-entry `(AttributeDetail ~a) (aevt a))))))

(e/defn DbStats []
  (r/pop
    (BrowsePath (e/server (map-entry `DbStats (d/db-stats db))))))

(e/defn EntityDetail [e]
  (r/pop
    (BrowsePath (e/server (map-entry `(EntityDetail ~e) (d/entity db e))))))

;; TODO mismacth, Datoms are vector-like
;; TODO e a should be refs so we can render them as links
#?(:clj (defn tx-detail [conn e hfql-spec search]
          (->> (d/tx-range (d/log conn) e (inc e))
            (eduction (mapcat :data)
              (map (fn [[e a v tx op]]
                     (let [m {:e e, :a a, :v v, :tx tx, :op op}]
                       ((hf-pull hfql-spec) {'% m}))))
              (filter #(contrib.str/includes-str? % search))))))

(e/defn TxDetail [e]
  (r/pop
    (BrowsePath (e/server (map-entry `(TxDetail ~e) (tx-detail conn e *hfql-spec ""))))))

#?(:clj (defn summarize-attr* [?!a]
          (let [db @dustingetz.mbrainz/test-db] ; todo hfql binding conveyance
            (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " "))))))

#?(:clj (defn attributes-count [{:keys [datoms attrs] :as m}]
          (->> (update-vals attrs :count)
            (into [] (map (fn [[k v]] {:key k, :count v})))
            (sort-by :count #(compare %2 %))
            vec)))

(e/defn Attribute [?e a v pull-expr]
  (Render ?e a (e/server (:db/ident (d/entity db v))) pull-expr))

(e/defn EntityTooltip [_?e _a v _pull-expr] ; questionable, oddly similar to hf/Render signature
  (e/server (contrib.str/pprint-str (e/server (d/pull db ['*] v)))))

#?(:clj (def !sitemap
          (atom      ; picker routes should merge into colspec as pull recursion
            {`Attributes [(with-meta 'db/ident {:hf/link `(AttributeDetail ~'db/ident)
                                                :hf/Tooltip `EntityTooltip})
                          `(summarize-attr* ~'%)
                          #_'*]
             `DbStats [:datoms `(attributes-count ~'%)] ; TODO render shorter name for `(attributes-count %)`
                                        ; TODO custom key/value renderers - conflict with treelister
             `AttributeDetail [(with-meta 'e {:hf/link `(EntityDetail ~'e)
                                              :hf/Tooltip `EntityTooltip})
                               :v
                               (with-meta 'tx {:hf/link `(TxDetail ~'tx)
                                               :hf/Tooltip `EntityTooltip})
                               #_:added]
             `TxDetail [(with-meta 'e {:hf/link `(EntityDetail ~'e)
                                       :hf/Tooltip `EntityTooltip})
                        (with-meta 'a {:hf/link `(AttributeDetail ~'a)
                                       :hf/Render `Attribute
                                       :hf/Tooltip `EntityTooltip})
                        :v]
             `EntityDetail ['*
                            #_{(ground :country/GB) []}
                            #_{'* '...}
                            #_(with-meta '* {:hf/link `(EntityDetail ~'*)})]
             `SiteMap ['*]})))

(e/defn SiteMap []
  (r/pop
    (BrowsePath (e/server (map-entry `(SiteMap) (e/watch !sitemap))))))

(comment
  (swap! !sitemap update-in [`Attributes] conj :db/id)
  (swap! !sitemap update-in [`Attributes] (constantly [:db/ident]))
  (swap! !sitemap update-in [`Attributes] (constantly [:db/ident `(summarize-attr* ~'%)]))
  (-> @!sitemap (get `Attributes) first meta :hf/link)
  )

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
                   `SiteMap SiteMap}
            eb/whitelist {`Attribute Attribute
                          `EntityTooltip EntityTooltip}
            conn conn
            db (e/server (ex/Offload-latch #(d/db conn)))]
    (dom/style (dom/text css tooltip/css))
    (let [sitemap (e/server (e/watch !sitemap))]
      (Index sitemap)
      (TooltipArea (e/fn []
                     (Tooltip)
                     (HfqlRoot sitemap :default `[[Attributes]]))))))

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
