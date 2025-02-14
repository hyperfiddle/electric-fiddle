(ns datomic-browser.dbob
  (:require [contrib.assert :as ca]
            [contrib.data :as cd :refer [map-entry]]
            [clojure.string :as str]
            [contrib.str :as cstr]
            #?(:clj [datomic.api :as d])
            [datomic-browser.datomic-browser :refer [Inject-datomic]]
            #?(:clj [datomic-browser.datomic-model :as da-model :refer
                     [attributes-stream ident! entity-history-datoms-stream easy-attr
                      summarize-attr is-attr? seq-consumer]])
            #?(:clj [dustingetz.datomic-contrib :as dx]) ; datafy entity
            [dustingetz.easy-table :refer [Load-css]]
            [dustingetz.entity-browser3 :as eb :refer [TableBlock TreeBlock Render]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages NotFoundPage]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            [missionary.core :as m]
            #?(:clj [dustingetz.y2020.hfql.hfql11 :refer [hf-pull]])
            [dustingetz.treelister3 :as tl]
            [contrib.debug :as dbg]))

(e/declare conn)
(e/declare db)
(e/declare *hfql-spec)

#?(:clj (defn attributes [db hfql-spec search]
          (->> (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            (eduction
              (map #(d/entity db %))
              (map (fn [!e] [!e ((hf-pull hfql-spec) {'% !e})])) ; pull everything for search
              (filter #(contrib.str/includes-str? (nth % 1) search)) ; search all pulled cols
              (map first)) ; unpull so we can datafy entity in view to infer cols
            sequence
            (sort-by (first hfql-spec)))))

(comment
  (require '[dustingetz.mbrainz :refer [test-db]])
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "ref one"))) := 18
  (time (count (attributes @dustingetz.mbrainz/test-db [:db/ident `(summarize-attr* ~'%) #_'*] "sys"))) := 3)

(e/defn Attributes []
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `Attributes #(attributes db *hfql-spec %)))
      nil *hfql-spec)))

#?(:clj (defn aevt [a]
          (let [db @dustingetz.mbrainz/test-db]
            (->> (d/datoms db :aevt a) (sort-by :v) (map (fn [[e a v tx added]] {:e e, :a a, :v v, :tx tx, :added added}))))))

(comment
  (time (count (aevt :abstractRelease/name))) := 10180
  (clojure.datafy/datafy (first (aevt :abstractRelease/name))))

(e/defn AttributeDetail [a]
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `(AttributeDetail ~a) (fn [search] (when a (aevt a)))))
      nil *hfql-spec
      #_#_:Row (e/fn [hfql-spec cols x]
             (e/server
               (let [[e _ v tx op] x]
                 (dom/td (r/link ['.. [`EntityDetail e]]
                           (dom/text e)
                           (dom/props {:data-tooltip (e/server (contrib.str/pprint-str (e/server (d/pull db ['*] e))))})))
                 (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
                 (dom/td (r/link ['.. [`TxDetail tx]] (dom/text tx)))))))))

;; fork entire treelist3 to control datomic EntityMap traversal
;; traverse into first (root) entity, don't traverse further
#?(:clj
   (defn data-children [x]
     (cond (instance? datomic.query.EntityMap x) x ; (coll? EntityMap) true, we want to control deep traversal
           (map? x) (seq x)
           (map-entry? x) (when-some [v* (data-children (val x))]
                            (let [k (key x)]
                              (mapv (fn [[p v]] [[k p] v]) v*)))
           (coll? x) (into [] (map-indexed vector) x)
           :else nil)))

#?(:clj
   (defn -tree-list [path x children-fn keep? root?]
     (let [c*? (children-fn x)
           c* (if (instance? datomic.query.EntityMap c*?)
                (when root? c*?)
                c*?)]
       (if-some [c* (seq c*)]
         (when-some [v* (seq (into [] (mapcat (fn [[p v]] (-tree-list (conj path p) v children-fn keep? false))) c*))]
           (cons [path x true] v*))
         ;;           search value and last path (is this heuristic correct?)
         (when (keep? [x (peek path)]) [[path x]])))))

#?(:clj
   (defn treelist
     ([x] (treelist data-children x))
     ([children-fn x] (treelist children-fn (constantly true) x))
     ([children-fn keep? x]
      (-tree-list [] x children-fn keep? :root))))

(e/defn DbStats []
  (e/client
    (TreeBlock ::db-stats
      (e/server (map-entry `DbStats (d/db-stats db)))
      nil
      :cols *hfql-spec)))

(e/defn EntityDetail [e]
  (e/client
    (TreeBlock ::select-user
      (e/server (map-entry `(EntityDetail ~e) (d/entity db e)))
      nil
      :cols *hfql-spec)))

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
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `(TxDetail ~e) (fn [search] (when e (tx-detail conn e *hfql-spec search)))))
      nil *hfql-spec)))

#?(:clj (defn summarize-attr* [?!a]
          (let [db @dustingetz.mbrainz/test-db] ; todo hfql binding conveyance
            (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " "))))))

#?(:clj (defn attributes-count [{:keys [datoms attrs] :as m}]
          (->> (update-vals attrs :count)
            (clojure.set/map-invert)
            (into (sorted-map-by #(compare %2 %1))))))

(e/defn Attribute [?e a v pull-expr]
  (Render ?e a (e/server (:db/ident (d/entity db v))) pull-expr))

(e/defn EntityTooltip [_?e _a v _pull-expr] ; questionable, oddly similar to hf/Render signature
  (e/server (contrib.str/pprint-str (e/server (d/pull db ['*] v)))))

#?(:clj (defn backrefs [!e] (update-vals (dx/back-references (d/entity-db !e) (:db/id !e)) count)))

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
                            #_(with-meta '* {:hf/link `(EntityDetail ~'*)})]})))

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
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn HfqlRoot
  [sitemap
   & {:keys [default]
      :or {default nil}}]
  (e/client
    #_(dom/pre (dom/text (pr-str r/route)))
    (Load-css "dustingetz/easy_table.css")
    (dom/div (dom/props {:class (str "Browser dustingetz-EasyTable")})
      (e/for [route (e/diff-by identity (e/as-vec r/route))] ; reboot entire page
        (binding [r/route route]
          (let [[fiddle & _] (first r/route)]
            (if-not fiddle
              (r/ReplaceState! ['. default])
              (let [Fiddle (get pages fiddle NotFoundPage)]
                (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Hyperfiddle"))
                (binding [*hfql-spec (e/server (get sitemap fiddle []))] ; cols don't serialize perfectly yet fixme
                  (e/Apply Fiddle (nfirst r/route)))))))))))

(e/defn Fiddles []
  {`DatomicBrowserOB (Inject-datomic dustingetz.mbrainz/mbrainz-uri
                       (e/fn [conn]
                         (binding [pages {`Attributes Attributes
                                          `AttributeDetail AttributeDetail
                                          `DbStats DbStats
                                          `TxDetail TxDetail
                                          `EntityDetail EntityDetail}
                                   eb/whitelist {`Attribute Attribute
                                                 `EntityTooltip EntityTooltip}
                                   conn conn
                                   db (e/server (ex/Offload-latch #(d/db conn)))]
                           (dom/style (dom/text css tooltip/css))
                           (let [sitemap (e/server (e/watch !sitemap))]
                             (Index sitemap)
                             (TooltipArea (e/fn []
                                            (Tooltip)
                                            (HfqlRoot sitemap :default `[[Attributes]])))))))})

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
