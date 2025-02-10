(ns datomic-browser.dbob
  (:require [dustingetz.entity-browser2 :as eb]
            [contrib.assert :as ca]
            [contrib.data :as cd :refer [map-entry]]
            [clojure.string :as str]
            [contrib.str :as cstr]
            #?(:clj [datomic.api :as d])
            [datomic-browser.datomic-browser :refer [Inject-datomic]]
            #?(:clj [datomic-browser.datomic-model :as da-model :refer
                     [attributes-stream ident! entity-history-datoms-stream easy-attr
                      summarize-attr is-attr? seq-consumer]])
            #?(:clj dustingetz.datomic-contrib) ; datafy entity
            [dustingetz.entity-browser1 :refer [HfqlRoot *hfql-spec]]
            [dustingetz.entity-browser2 :refer [TableBlock TreeBlock]]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric3-contrib :as ex]
            [missionary.core :as m]
            #?(:clj [dustingetz.y2020.hfql.hfql11 :refer [hf-pull]])))

(e/declare conn)
(e/declare db)

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

(e/defn DbStats []
  ;; TODO search should filter keys, not values (numbers)
  (e/client
    (TreeBlock ::db-stats
      (e/server (map-entry `DbStats (d/db-stats db)))
      nil :cols *hfql-spec)))

#?(:clj (defn aevt [a]
          (let [db @dustingetz.mbrainz/test-db]
            (->> (d/datoms db :aevt a) (sort-by :v)))))

(comment
  (time (count (aevt :abstractRelease/name))) := 10180
  (clojure.datafy/datafy (first (aevt :abstractRelease/name))))

(e/defn AttributeDetail [a]
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `(AttributeDetail ~a) (fn [search] (when a (aevt a)))))
      nil *hfql-spec
      #_#_:Row (e/fn [cols x]
             (e/server
               (let [[e _ v tx op] x]
                 (dom/td (r/link ['.. [`EntityDetail e]] (dom/text e)))
                 (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
                 (dom/td (r/link ['.. [`TxDetail tx]] (dom/text tx)))))))))

(e/defn EntityDetail [e]
  (e/client
    (TreeBlock ::select-user
      (e/server (map-entry `(EntityDetail ~e) (d/entity db e)))
       nil :cols *hfql-spec)))

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
      (e/server (map-entry `(TxDetail ~e) (fn [search] (tx-detail conn e *hfql-spec search))))
      nil *hfql-spec)))

#?(:clj (defn summarize-attr* [?!a]
          (let [db @dustingetz.mbrainz/test-db] ; todo hfql binding conveyance
            (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " "))))))

#?(:clj (defn attributes-count [x] (-> x :attrs (update-vals :count))))

#?(:clj (def !sitemap
          (atom ; picker routes should merge into colspec as pull recursion
            {`Attributes [(with-meta 'db/ident {:hf/link `(AttributeDetail ~'db/ident)})
                          `(summarize-attr* ~'%)
                          #_'*]
             `DbStats [:datoms `(attributes-count ~'%)] ; TODO render shorter name for `(attributes-count %)`
             `AttributeDetail [:e :a :v :tx #_:added]
             `TxDetail [:e :a :v]
             `EntityDetail ['*]})))

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
    (r/link ['. [`Attributes]] (dom/text "Attributes"))
    (r/link ['. [`DbStats]] (dom/text "DbStats"))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn Fiddles []
  {`DatomicBrowserOB (Inject-datomic dustingetz.mbrainz/mbrainz-uri
                       (e/fn [conn]
                         (binding [pages {`Attributes Attributes
                                          `AttributeDetail AttributeDetail
                                          `DbStats DbStats
                                          `TxDetail TxDetail
                                          `EntityDetail EntityDetail}
                                   conn conn
                                   db (e/server (ex/Offload-latch #(d/db conn)))]
                           (dom/style (dom/text css))
                           (let [sitemap (e/server (e/watch !sitemap))]
                             (Index sitemap)
                             (HfqlRoot sitemap :default `(Attributes))))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser .-datomic-browser-dbob-db-stats table { grid-template-columns: 36ch auto;}

"
)
