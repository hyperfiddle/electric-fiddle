(ns datomic-browser.datomic-browser4
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.nav0 :as hfp]
            electric-fiddle.fiddle-index
            [peternagy.hfql #?(:clj :as :cljs :as-alias) hfql]
            [clojure.string :as str]
            [clojure.walk]
            [clojure.datafy :as datafy]
            [clojure.core.protocols :as ccp]
            [dustingetz.entity-browser4 :as eb]
            [dustingetz.str :as strx]
            [missionary.core :as m]
            [edamame.core :as eda]
            [contrib.debug :as dbg]
            [clojure.tools.reader :as ctr]
            #?(:clj [datomic.api :as d])
            #?(:clj [dustingetz.datomic-contrib2 :as datomicx])))

(e/declare ^:dynamic conn)
(e/declare ^:dynamic db)
(e/declare ^:dynamic db-stats)

#?(:clj
   (extend-protocol hfql/Suggestable
     datomic.query.EntityMap
     (-suggest [!e]
       (mapv (fn [k]
               {:label k, :entry k})
         (into [:db/id] cat [(keys (d/touch !e))
                             (into [] (comp (map first) (distinct) (map datomicx/invert-attribute))
                               (datomicx/reverse-refs (d/entity-db !e) (:db/id !e)))])))))

;;;;;;;;;;;;;
;; QUERIES ;;
;;;;;;;;;;;;;

#?(:clj (defn attributes []
          (with-meta
            (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            {`clojure.core.protocols/nav (fn [xs k v] (d/entity db v))})))
#?(:clj (defn attribute-count [!e] (-> db-stats :attrs (get (:db/ident !e)) :count)))

#?(:clj
   (comment
     (require '[dustingetz.mbrainz :refer [test-db lennon pour-lamour yanne cobblestone]])
     (datafy/nav (d/entity @test-db pour-lamour) :abstractRelease/type :abstractRelease/type)
     (first ((binding [db @test-db] (attributes)) "artists" [[:db/ident :asc]]))
     (attribute-count (d/entity @test-db :abstractRelease/type))
     (eb/->sort-comparator [[:db/ident :asc]])
     (time (count (->> (attributes @test-db) (hfql-search-sort {#'db @test-db} [:db/ident `(summarize-attr* ~'%) #_'*] "sys")))) := 3))

#?(:clj (defn datom->map [[e a v tx added]]
          (let [db db]
            (with-meta {:e e, :a a, :v v, :tx tx, :added added}
              {`hfp/-identify hash
               `ccp/nav (fn [_this k v] (if (= :a k) (d/entity db a) v))}))))

#?(:clj (defn attribute-detail [a]
          (let [db db]
            (with-meta (d/q '[:find [?e ...] :in $ ?a :where [?e ?a]] db a)
              {`ccp/nav (fn [_this _k v] (d/entity db v))}))))

#?(:clj (defn tx-detail [e] (->> (d/tx-range (d/log conn) e (inc e)) (into [] (comp (mapcat :data) (map datom->map))))))

#?(:clj (defn summarize-attr* [?!a] #_[db]
          (when ?!a (->> (datomicx/easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (str/join " ")))))

#?(:clj (defn entity-history [e] #_[db]
          (let [history (d/history db)]
            (into [] (comp cat (map datom->map))
              [(d/datoms history :eavt (:db/id e e)) ; resolve both data and object repr, todo revisit
               (d/datoms history :vaet (:db/id e e))]))))

#?(:clj (defn entity-detail [e] (d/entity db e)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PROGRESSIVE ENHANCEMENTS ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(e/defn EntityTooltip [v o spec]
  (e/server (strx/pprint-str (e/server (d/pull db ['*] v)))))

(e/defn SemanticTooltip [v o spec]
  (e/server
    (when-not (coll? v)
      ;; `and` is glitch guard, TODO remove
      (let [k (and spec (hfql/unwrap spec))
            [typ _ unique?] (datomicx/easy-attr db k)]
        (cond
          (= :db/id k) (EntityTooltip v o spec)
          (= :ref typ) (strx/pprint-str (d/pull db ['*] v))
          (= :identity unique?) (strx/pprint-str (d/pull db ['*] [(hfql/unwrap spec) #_(:db/ident (d/entity db a)) v])) ; resolve lookup ref
          () nil)))))

(e/defn TxDetailValueTooltip [v o spec]
  (e/server
    (let [a (get v :a)
          [typ _ unique?] (datomicx/easy-attr db a)]
      (cond
        (= :ref typ) (strx/pprint-str (d/pull db ['*] v))
        (= :identity unique?) (strx/pprint-str (d/pull db ['*] [a #_(:db/ident (d/entity db a)) v])) ; resolve lookup ref
        () nil))))

;; glitch guard, TODO remove
#?(:clj (defn safe-long [v] (if (number? v) v 1)))
(e/defn EntityDbidCell [v o spec]
  (let [v2 (e/server (safe-long v))]
    (dom/span (dom/text v2 " ") (r/link ['. [`(entity-history ~v2)]] (dom/text "entity history")))))

#?(:clj (defn route-to-entity-detail [o] (when (instance? datomic.query.EntityMap o) (list `entity-detail (:db/id o)))))

;;;;;;;;;;;;;
;; SITEMAP ;;
;;;;;;;;;;;;;

#?(:clj (def sitemap-path "datomic_browser/datomic_browser4.edn"))
#?(:clj (defn sitemap-writer [file-path] (fn [v] (spit file-path (strx/pprint-str v)))))
#?(:clj (def this-ns *ns*))

;;;;;;;;;;;;;;;;
;; ENTRYPOINT ;;
;;;;;;;;;;;;;;;;

(defn find-context-free-pages [sitemap]
  (sort-by first (filterv #(not (next %)) (keys sitemap))))

(e/defn Index [sitemap]
  (dom/nav
    (dom/props {:class "Index"})
    (dom/text "Nav:")
    (e/for [view (e/diff-by {} (e/server (find-context-free-pages sitemap)))]
      (dom/text " ") (r/link ['. [view]] (dom/text (name (first view)))))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn DatomicBrowser4 [conn]
  (binding [eb/whitelist {`EntityTooltip EntityTooltip
                          `TxDetailValueTooltip TxDetailValueTooltip
                          `SemanticTooltip SemanticTooltip
                          `EntityDbidCell EntityDbidCell}
            conn conn
            db (e/server (ex/Offload-latch #(d/db conn)))] ; electric binding
    (binding [eb/*hfql-bindings (e/server {(find-var `db) db, (find-var `conn) conn, (find-var `db-stats) (e/server (d/db-stats db))})
              eb/*sitemap (e/server (eb/read-sitemap sitemap-path this-ns))
              eb/*sitemap-writer (e/server (sitemap-writer sitemap-path))
              eb/*page-defaults (e/server [route-to-entity-detail])]
      (let [sitemap eb/*sitemap]
        (dom/style (dom/text css))
        (dom/link (dom/props {:href "/hyperfiddle/electric-forms.css"}))
        (Index sitemap)
        (eb/HfqlRoot sitemap `[(attributes)])))))

(def css "

html { scrollbar-gutter: stable; } /* prevent layout jump when scrollbar shows/hide */

/* Explicit table height - there are alternatives */
.Browser fieldset.dustingetz-entity-browser4__block table { height: calc(16 * var(--row-height)); } /* 15 rows + header row */
.Browser fieldset.dustingetz-entity-browser4__block { height: fit-content; }

/* Progressive enhancement */
.Browser fieldset.entity table { grid-template-columns: 15em auto; }
.Browser.datomic-browser-datomic-browser4-DbStats .entity-children table { grid-template-columns: 36ch auto;}

"
  )

;; /*
;;  Let table pickers fill available vertical space.
;;  Table pickers will expand to fill available vertical space by default, unless given an explicit CSS height or max-height.
;;  Here we make sure their parent containers do provide available space for pickers to expand in.
;; */
;; body.electric-fiddle { height: 100dvh; box-sizing: border-box; }
;; :not(body):has(.hyperfiddle-electric-forms5__table-picker) { height: 100%; }


(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
                                        ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) (Busy)
    (or (some? (ex-message ?x)) (nil? ?x)) (Failed ?x)
    () (Ok ?x)))

(e/defn Inject-datomic [datomic-uri F]
  (e/fn []
    (e/server
      (Inject (e/Task (m/via m/blk
                        (try #_(check) (d/connect datomic-uri)
                             (catch Exception e #_(log/error e) e))))
        {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
         :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                   (dom/pre (dom/text (pr-str err))))
         :Ok F}))))

(comment
  (require '[clojure.edn :as edn])
  (edn/read-string (pr-str '{a 1}))
  (require '[edamame.core :as eda])
  (eda/parse-string (slurp "./src/datomic_browser/datomic_browser4.edn")
    {:auto-resolve (fn [x] (if (= :current x) *ns* (get (ns-aliases *ns*) x)))})
  (let [db @dustingetz.mbrainz/test-db]
    (reduce (fn [[_e kc :as ac] nx]
              (let [mp (d/touch (d/entity db nx))
                    k* (keys mp)]
                (if (> (count k*) kc)
                  [nx (count k*)]
                  ac)))
      [0 0] (d/q '[:find [?e ...] :in $ :where [?e :release/name]] db)))
  [17592186077296 15]
  )
