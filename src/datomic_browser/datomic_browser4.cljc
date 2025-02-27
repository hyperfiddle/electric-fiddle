(ns datomic-browser.datomic-browser4
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric3-contrib :as ex]
            electric-fiddle.fiddle-index
            [peternagy.hfql #?(:clj :as :cljs :as-alias) hfql]
            [clojure.string :as str]
            [clojure.walk]
            [dustingetz.entity-browser4 :as eb]
            [dustingetz.str :as strx]
            [missionary.core :as m]
            [edamame.core :as eda]
            [clojure.tools.reader :as ctr]
            #?(:clj [clojure.core.protocols :as ccp])
            #?(:clj [datomic.api :as d])
            #?(:clj [dustingetz.datomic-contrib2 :as datomicx])))

(e/declare conn)
(e/declare ^:dynamic db)

#?(:clj (defn attributes []
          (with-meta
            (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            {`ccp/nav (fn [_xs _k v] (d/entity db v))
             `hfql/-suggest (fn [o] (keys (d/touch (ccp/nav o nil (first o)))))})))

#?(:clj (defn datom->map [[e a v tx added]]
          (with-meta {:e e, :a a, :v v, :tx tx, :added added}
            {`-identify (fn [_] e)})))

#?(:clj (defn attribute-detail [a] (->> (d/datoms db :aevt a) (sort-by :v) (map datom->map)))) ; todo inline

#?(:clj (defn db-stats [] (d/db-stats db)))

#?(:clj (defn tx-detail [e] (->> (d/tx-range (d/log conn) e (inc e)) (eduction (mapcat :data) (map datom->map)))))

#?(:clj (defn summarize-attr* [?!a] #_[db]
          (when ?!a (->> (datomicx/easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (str/join " ")))))

#?(:clj (defn attributes-count [{:keys [attrs]}]
          (->> (update-vals attrs :count)
            (into [] (map (fn [[k v]] {:key k, :count v})))
            (sort-by :count #(compare %2 %))
            vec)))

(e/defn AttributeCell [?e a v pull-expr]
  (eb/Render ?e a (e/server (:db/ident (d/entity db v))) pull-expr))

(e/defn EntityTooltip [_?e _a v _pull-expr] ; questionable, oddly similar to hf/Render signature
  (e/server (strx/pprint-str (e/server (d/pull db ['*] v)))))

(e/defn SemanticTooltip [_?e a v _pull-expr]
  (e/server
    (let [[typ _ unique?] (datomicx/easy-attr db a)]
      (cond
        (= :ref typ) (strx/pprint-str (d/pull db ['*] v))
        (= :identity unique?) (strx/pprint-str (d/pull db ['*] [a #_(:db/ident (d/entity db a)) v])) ; resolve lookup ref
        () nil))))

(e/defn TxDetailValueTooltip [?e _a v _pull-expr]
  (e/server
    (let [a (get ?e 'a) ; symbolic why
          [typ _ unique?] (datomicx/easy-attr db a)]
      (cond
        (= :ref typ) (strx/pprint-str (d/pull db ['*] v))
        (= :identity unique?) (strx/pprint-str (d/pull db ['*] [a #_(:db/ident (d/entity db a)) v])) ; resolve lookup ref
        () nil))))

#?(:clj (defn entity-history [e] #_[db]
          (let [history (d/history db)]
            (eduction cat (map datom->map)
              [(d/datoms history :eavt (:db/id e e)) ; resolve both data and object repr, todo revisit
               (d/datoms history :vaet (:db/id e e))]))))

#?(:clj (defn entity-detail [e] (d/entity db e)))

(e/defn EntityDbidCell [_?e _a v _pull-expr]
  (dom/text v " ") (r/link ['.. [`(EntityHistory ~v)]] (dom/text "entity history")))

;; #?(:clj (def sitemap
;;           (hfql/template
;;             {attributes           [(hfql/props :db/ident {::hfql/link    (attribute-detail :db/ident)
;;                                                           ::hfql/Tooltip EntityTooltip})
;;                                    (summarize-attr* %)
;;                                    :db/doc]
;;              db-stats             [:datoms
;;                                    (attributes-count %)]
;;              (attribute-detail a) [(hfql/props :e {::hfql/link    (entity-detail e)
;;                                                    ::hfql/Tooltip EntityTooltip})
;;                                    :v
;;                                    (hfql/props :tx {::hfql/link    (tx-detail tx)
;;                                                     ::hfql/Tooltip EntityTooltip})]
;;              (tx-detail tx)       [(hfql/props :e {::hfql/link    (entity-detail e)
;;                                                    ::hfql/Tooltip EntityTooltip})
;;                                    (hfql/props :a {::hfql/link    (attribute-detail a)
;;                                                    ::hfql/Render  AttributeCell
;;                                                    ::hfql/Tooltip EntityTooltip})
;;                                    (hfql/props :v {::hfql/Tooltip TxDetailValueTooltip})]
;;              (entity-detail e)    (hfql/props [(hfql/props :db/id {::hfql/Render EntityDbidCell}) ; TODO want link and Tooltip instead
;;                                                *]
;;                                     {::hfql/Tooltip SemanticTooltip
;;                                      ;; ::hf/select (entity-detail %)  not a thing right now
;;                                      })
;;              (entity-history e)   [*]})))

;; ;; alternative syntax with hf/props inlined as a map
;; #?(:clj (def sitemap2
;;           (hfql/template
;;             {attributes           [:db/ident {::hfql/link (attribute-detail :db/ident) ::hfql/Tooltip EntityTooltip}
;;                                    (summarize-attr* %)
;;                                    :db/doc]
;;              db-stats             [:datoms
;;                                    (attributes-count %)]
;;              (attribute-detail a) [:e {::hfql/link (entity-detail e) ::hfql/Tooltip EntityTooltip}
;;                                    :v
;;                                    :tx {::hfql/link (tx-detail tx) ::hfql/Tooltip EntityTooltip}]
;;              (tx-detail tx)       [:e {::hfql/link (entity-detail e) ::hfql/Tooltip EntityTooltip}
;;                                    :a {::hfql/link (attribute-detail a) ::hfql/Render AttributeCell ::hfql/Tooltip EntityTooltip}
;;                                    :v {::hfql/Tooltip TxDetailValueTooltip}]
;;              (entity-detail e)    [{::hfql/Tooltip SemanticTooltip ; first map is global hf/props
;;                                     ;; ::hf/select (entity-detail %)  not a thing right now
;;                                     }
;;                                    :db/id {::hfql/Render EntityDbidCell} ; TODO want link and Tooltip instead
;;                                    *]
;;              (entity-history e)   [*]})))

;; works today
#?(:clj (def sitemap3
          {`attributes            [(hfql/props :db/ident {::hfql/link    `(attribute-detail :db/ident)
                                                          ::hfql/Tooltip `EntityTooltip})
                                   `(summarize-attr* ~'%)
                                   :db/doc]
           `db-stats              [:datoms
                                   `(attributes-count ~'%)]
           `(attribute-detail :a) [(hfql/props :e {::hfql/link    `(entity-detail :e)
                                                   ::hfql/Tooltip `EntityTooltip})
                                   :v
                                   (hfql/props :tx {::hfql/link    `(tx-detail :tx)
                                                    ::hfql/Tooltip `EntityTooltip})]
           `(tx-detail :tx)       [(hfql/props :e {::hfql/link    `(entity-detail :e)
                                                   ::hfql/Tooltip `EntityTooltip})
                                   (hfql/props :a {::hfql/link    `(attribute-detail :a)
                                                   ::hfql/Render  `AttributeCell
                                                   ::hfql/Tooltip `EntityTooltip})
                                   (hfql/props :v {::hfql/Tooltip `TxDetailValueTooltip})]
           `(entity-detail :e)    (hfql/props [(hfql/props :db/id {::hfql/Render `EntityDbidCell}) ; TODO want link and Tooltip instead
                                               '*]
                                    {::hfql/Tooltip `SemanticTooltip
                                     ;; ::hf/select (entity-detail %)  not a thing right now
                                     })
           `(entity-history :e)   ['*]}))

#?(:clj
   (defn normalize-sitemap [sitemap]
     (let [qualify #(symbol (resolve %))]
       (update-keys sitemap
         (fn [k]
           (if (symbol? k)
             (seq (list (qualify k)))
             (cons (qualify (first k)) (next k))))))))

#?(:clj (defn read-sitemap [file-path]
          (clojure.walk/postwalk
            (fn [x] (cond
                      (= `% x) '%
                      (and (seq? x) (= `hfql/props (first x))) (apply hfql/props (next x))
                      :else    x))
            (eval (read-string (str "`" (slurp file-path)))))))

;; #?(:clj (def sitemap4 (normalize-sitemap
;;                         (eda/parse-string (str "`" (slurp "./src/datomic_browser/datomic_browser4.edn"))
;;                           {:auto-resolve (fn [x] (if (= :current x) *ns* (get (ns-aliases *ns*) x)))
;;                            :syntax-quote {:resolve-symbol (fn [sym] (if (= sym '%) '% (ctr/resolve-symbol sym)))}}))))

#?(:clj (def sitemap-path "./src/datomic_browser/datomic_browser4.edn"))
#?(:clj (def sitemap5 (normalize-sitemap (read-sitemap sitemap-path))))
#?(:clj (defn sitemap-writer [file-path] (fn [v] (spit file-path (strx/pprint-str v)))))
#?(:clj (def !sitemap (atom sitemap5)))

(comment
  (do sitemap5)
  (def edn (slurp "./src/datomic_browser/datomic_browser4.edn"))
  (eval (eda/parse-string (str "`" (slurp "./src/datomic_browser/datomic_browser4.edn"))
     {:auto-resolve (fn [x] (if (= :current x) *ns* (get (ns-aliases *ns*) x)))
      :syntax-quote {:resolve-symbol (fn [sym] (if (= sym '%) '% (ctr/resolve-symbol sym)))}}))
  (read-sitemap "./src/datomic_browser/datomic_browser4.edn")
  (eval (read-string (str "`" edn)))
  )

(e/defn Index [sitemap]
  (dom/nav
    (dom/props {:class "Index"})
    (dom/text "Nav:")
    (e/for [view (e/diff-by {} (e/server (find-context-free-pages sitemap)))]
      (dom/text " ") (r/link ['. [view]] (dom/text (name (first view)))))
    (dom/text " — Datomic Browser")))

(declare css)
(e/defn DatomicBrowser4 [conn]
  (binding [electric-fiddle.fiddle-index/pages {} ; TODO these don't exist, is this needed?
            #_
            {`Attributes Attributes
             `AttributeDetail AttributeDetail
             `DbStats DbStats
             `TxDetail TxDetail
             `EntityDetail EntityDetail
             `EntityHistory EntityHistory}
            eb/whitelist
            {`AttributeCell AttributeCell
             `EntityTooltip EntityTooltip
             `TxDetailValueTooltip TxDetailValueTooltip
             `SemanticTooltip SemanticTooltip
             `EntityDbidCell EntityDbidCell}
            conn conn
            db (e/server (ex/Offload-latch #(d/db conn)))] ; electric binding
    (binding [eb/*hfql-bindings (e/server {(find-var `db) db})
              eb/!sitemap !sitemap
              eb/*sitemap (e/watch !sitemap)
              eb/*sitemap-writer (e/server (sitemap-writer sitemap-path))]
      (let [sitemap eb/*sitemap]
        (dom/style (dom/text css))
        (Index sitemap)
        (eb/HfqlRoot sitemap `[(db-stats)])))))

(def css "

/* Explicit table height - there are alternatives */
.Browser fieldset.dustingetz-entity-browser3__block table { height: calc(16 * var(--row-height)); } /* 15 rows + header row */
.Browser fieldset.dustingetz-entity-browser3__block { height: fit-content; }

/* Progressive enhancement */
.Browser fieldset.entity table { grid-template-columns: 15em auto; }
.Browser.datomic-browser-datomic-browser3-DbStats .entity-children table { grid-template-columns: 36ch auto;}

"
  )

;; /*
;;  Let table pickers fill available vertical space.
;;  Table pickers will expand to fill available vertical space by default, unless given an explicit CSS height or max-height.
;;  Here we make sure their parent containers do provide available space for pickers to expand in.
;; */
;; body.electric-fiddle { height: 100dvh; box-sizing: border-box; }
;; :not(body):has(.hyperfiddle-electric-forms4__table-picker) { height: 100%; }


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
  )
