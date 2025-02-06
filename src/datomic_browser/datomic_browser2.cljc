(ns datomic-browser.datomic-browser2
  (:require clojure.string
            [contrib.assert :refer [check]]
            [datomic-browser.contrib :refer
             [clamp-left treelister flatten-nested includes-str?]]
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :refer
                     [attributes-stream ident! entity-history-datoms-stream easy-attr
                      summarize-attr is-attr? seq-consumer]])
            #?(:clj contrib.datomic-contrib)
            [contrib.orderedmap :refer [ordered-map]]
            #?(:clj [clojure.pprint :refer [cl-format]])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Input* Input! Form!]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router4 :as r]
            [missionary.core :as m]))

(e/defn TableScroll [record-count ?xs! Row]
  (e/server
    (dom/div (dom/props {:class "Viewport"})
      (let [row-height 24
            [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
        (dom/table
          (e/for [i (IndexRing limit offset)]
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              (Row (nth (vec ?xs!) i nil)))))
        (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                    (* row-height (- record-count limit)) 0) "px")}}))))))

(e/declare conn)
(e/declare db)

#_
(e/defn SearchGrid [title Query Row]
  (let [search (e/client (r/pop (first r/route)))
        xs! (e/server (Query search)) ; wtf
        n (e/server (count xs!))]
    (dom/fieldset
      (dom/legend (dom/text title " ")
                  (e/client (r/pop (r/ReplaceState! ['. [(Input* search)]])))
                  (dom/text " (" n " items)"))
      (TableScroll n xs! Row))))

(e/defn SearchGrid [title Query Row & {:keys [search SetSearch] :or {search "", SetSearch (e/fn [_])}}]
  (let [xs! (e/server (Query search)) ; wtf
        n (e/server (count xs!))]
    (dom/fieldset
      (dom/legend (dom/text title " ")
                  (e/client (SetSearch (Input* search)))
                  (dom/text " (" n " items)"))
      (TableScroll n xs! Row))))

(def attributes-colspec
  [:db/ident :db/unique :db/isComponent
   {:db/valueType [:db/ident]}
   {:db/cardinality [:db/ident]}
   #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs])

(e/defn Attributes []
  (let [[search] r/route]
    (SearchGrid "Attributes"
      (e/fn Query [search]
        (e/server (->> (attributes-stream db attributes-colspec) (m/reduce conj []) e/Task
                    (map #(assoc % ::summary (->> (summarize-attr db (:db/ident %)) (map name) (clojure.string/join " "))))
                    (filter #(includes-str? ((juxt :db/ident ::summary) %) search)) ; search after summary
                    (sort-by :db/ident))))
      (e/fn Row [x]
        (dom/td (let [v (:db/ident x)] (r/link ['.. [:attribute v]] (dom/text v))))
        (dom/td (dom/text (::summary x))))
      :search search
      :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [new-search]])))))

(e/defn AttributeDetail []
  (let [[a search] r/route]
    #_(r/focus [1]) ; search
    (when a ; router glitch
      (SearchGrid (str "Attribute index: " (pr-str a))
        (e/fn Query [search]
          (e/server (->> (seq-consumer (d/datoms db :aevt a)) (m/reduce conj []) e/Task
                      (filter #(includes-str? (:v %) search))
                      (sort-by :v))))
        (e/fn Row [x]
          (e/server
            (let [[e _ v tx op] x]
              (dom/td (r/link ['.. [:entity e]] (dom/text e)))
              #_(dom/td (dom/text (pr-str a))) ; redundant
              (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
              (dom/td (r/link ['.. [:tx-detail tx]] (dom/text tx))))))
        :search search
        :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [a new-search]]))))))

(e/defn TxDetail []
  (let [[e search] r/route]
    #_(r/focus [1]) ; search
    (SearchGrid (str "Tx detail: " e)
      (e/fn Query [search]
        (e/server
          (when e ; glitch
            (->> (seq-consumer (d/tx-range (d/log conn) e (inc e))) ; global
              (m/eduction (map :data) cat) (m/reduce conj []) e/Task
              (filter #(includes-str? (str ((juxt :e #_:a :v #_:tx) %)) search)))))) ; string the datom, todo resolve human attrs
      (e/fn Row [[e aa v tx op]]
        (dom/td #_(let [e (e/server (e/Task (ident! db e)))]) (r/link ['.. [:entity e]] (dom/text e)))
        (dom/td (let [aa (e/server (e/Task (ident! db aa)))] (r/link ['.. [:attribute aa]] (dom/text aa))))
        (dom/td (dom/text (pr-str v))) ; todo if a is ref, present link
        #_(dom/td (r/link ['.. [:tx-detail tx]] (dom/text tx))) ; redundant
        )
      :search search
      :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [e new-search]])))))

;; copied from contrib.datomic-contrib because lack of .cljc - TODO put in proper shared ns
(defn reverse-attribute? [attribute]
  {:pre [(qualified-keyword? attribute)]}
  (clojure.string/starts-with? (name attribute) "_"))

;; copied from contrib.datomic-contrib because lack of .cljc - TODO put in proper shared ns
(defn revert-attribute [attribute]
  {:pre [(qualified-keyword? attribute)]}
  (let [nom (name attribute)]
    (keyword (namespace attribute) (if (reverse-attribute? attribute) (subs nom 1) (str "_" nom)))))

(defn absolute-attribute [attr] ; TODO move to shared ns
  (if (reverse-attribute? attr)
    (revert-attribute attr)
    attr))

(defn safe [f pred] (fn [x] (if (pred x) (f x) x))) ; hack around glitches

(e/defn EntityTooltip [e]
  (e/client
    (dom/pre (dom/props {:class "entity-tooltip"}) (dom/text (contrib.str/pprint-str (e/server (d/pull db ['*] e)))))))

(e/defn EntityLink [e]
  (e/When e
    (r/pop (r/link ['. [[:entity e]]] (dom/text e)))
    (EntityTooltip e)))

(e/defn Format-entity [e {:keys [path name value] :as ?row}]
  (e/server ; keep vals on server, row can contain refs
    (let [k name v value]
      ;; (e/for [[?row k v] (e/diff-by identity (e/as-vec [?row k v]))])
      (when ?row
        (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
                (cond
                  (= :db/id k) (dom/text k) ; :db/id is our schema extension, can't nav to it
                  (integer? k) (dom/text k) ; indexed collection descent
                  (is-attr? db ((safe absolute-attribute keyword?) k))
                  (if ((safe reverse-attribute? keyword?) k)
                    (r/link ['.. [:attribute ((safe absolute-attribute keyword?) k) e]] (dom/text k))
                    (r/link ['.. [:attribute ((safe absolute-attribute keyword?) k)]] (dom/text k)))
                  () (dom/text (str k)))) ; str is needed for Long db/id, why?
        (dom/td
          (if-not (coll? v) ; don't render card :many intermediate row
            (let [[valueType cardinality] (easy-attr db k)]
              ;; HACK without the `e/for` we observed a conditional glitch where
              ;; the `:db/id` branch stayed alive with a `k` of `:db/doc`
              (e/for [v (e/diff-by identity (e/as-vec v))]
                (cond
                  (= :db/id k) (EntityLink v)
                  (= :ref valueType) (EntityLink v)
                  (= :string valueType) (dom/text v) #_(Form! (Input! k v) :commit (fn [] []) :show-buttons :smart)
                  () (dom/text (str v)))))
            (cond
              (reverse-attribute? k) (do (dom/props {:class "reverse-attr"})
                                         (dom/text (cl-format false "~d reference~:p" (count v))))
              () (e/amb))))))))

(letfn [(attribute-name [attribute] (if (reverse-attribute? attribute) (namespace attribute) (name attribute)))]
  (defn sort-by-attr [entity-like-map]
    (into (ordered-map)
      (sort-by (comp attribute-name key) entity-like-map))))

#?(:clj
   (defn- query-entity-detail [db e search]
     (if (and db e) ; hack around glitches
       (->> (flatten-nested #_(e/Task (m/via m/blk)) (sort-by-attr (merge (d/pull db ['*] e) (contrib.datomic-contrib/back-references db e)))) ; TODO render backrefs at the end?
         (filter #(includes-str? (str ((juxt :name :value) %)) search))) ; string the entries
       ()
       )))

(e/defn EntityDetail []
  (let [[[type e search] & other-blocks] r/route]
    #_(r/focus [1]) ; search
    (when e ; glitch
      (SearchGrid (str "Entity detail: " e)
        (e/fn Query [search]
          (e/server (query-entity-detail db e search)))
        (e/Partial Format-entity e)
        :search search
        :SetSearch (e/fn [new-search] (r/ReplaceState! ['. (cons [type e new-search] other-blocks)]))))))

(e/defn Format-history-row [[e aa v tx op :as ?row]]
  (when ?row ; glitch
    (dom/td (e/client (dom/text (name (case op true :db/add false :db/retract (e/amb))))))
    (dom/td (e/client (r/link ['.. [:entity e]] (dom/text e)))) ; link two levels up because we are under EntityHistory's scope
    (dom/td (if (some? aa)
              (let [ident (:db/ident (e/Task (m/via m/blk (d/pull db [:db/ident] aa))))]
                (dom/text (pr-str ident)))))
    (dom/td (some-> v pr-str dom/text))
    (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
    (dom/td (let [x (:db/txInstant (e/Task (m/via m/blk (d/pull db [:db/txInstant] tx))))]
              (dom/text (e/client (pr-str x)))))))

(e/defn EntityHistory []
  (let [[e search] r/route]
    #_(r/focus [1]) ; search
    (when e ; router glitch
      (SearchGrid (str "Entity history: " e)
        (e/fn [search]
          (e/server (->> (entity-history-datoms-stream db e) (m/reduce conj []) e/Task
                      (filter #(includes-str? (str ((juxt :e #_:a :v #_:tx) %)) search))))) ; todo resolve human attrs
        Format-history-row
        #_{:columns [::e ::a ::op ::v ::tx-instant ::tx]
           ::dom/class "Viewport entity-history"}
        :search search
        :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [e new-search]]))))))

(e/defn DbStats []
  #_(r/focus [0]) ; search
  (let [[search] r/route]
    (SearchGrid (str "Db stats")
      (e/fn Query [search]
        (e/server
          #_(flatten-nested (e/Task (m/via m/blk (d/db-stats db)))) ; need more control to inline short maps
          (seq ((treelister (fn [[k v]] (condp = k :attrs (into (sorted-map) v) nil))
                  (e/Task (m/via m/blk (d/db-stats db)))) search))))
      (e/fn Row [[tab [k v] :as ?row]] ; thead: [::k ::v]
        (when ?row
          (dom/td (dom/text (pr-str k)) (dom/props {:style {:padding-left (some-> tab (* 15) (str "px"))}}))
          (dom/td (cond
                    (= k :attrs) nil ; print children instead
                    () (dom/text (pr-str v))))))
      :search search
      :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [new-search]])))))

(comment
  (require '[dustingetz.mbrainz :refer [test-db test-conn lennon]])
  (d/db-stats @test-db)
  := {:datoms 800958
      :attrs
      {:release/script {:count 11435}
       :label/type {:count 870}
       ... ...}})

(e/defn RecentTx []
  #_(r/focus [0]) ; search
  (let [[search] r/route]
    (SearchGrid (str "Recent Txs")
      (e/fn Query [search]
        (e/server (->> (d/datoms db :aevt :db/txInstant) seq-consumer (m/reduce conj ()) e/Task
                    (filter #(includes-str? (str ((juxt :e #_:a :v #_:tx) %)) search)))))
      (e/fn Row [[e _ v tx op :as ?row]]
                                        ; columns [:db/id :db/txInstant]
        (when ?row
          (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
          (dom/td (dom/text (e/client (pr-str v)) #_(e/client (.toLocaleDateString v))))))
      :search search
      :SetSearch (e/fn [new-search] (r/ReplaceState! ['. [new-search]])))))

(e/defn Nav []
  (dom/div (dom/props {:class "nav"})
    (dom/text "Nav: ")
    (r/link ['. [:attributes]] (dom/text "attributes")) (dom/text " ")
    (r/link ['. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
    (r/link ['. [:recent-tx]] (dom/text "recent-tx"))
    (dom/text " — Datomic Browser2")))

(declare css)


#_(e/defn BrowsePath [locus x]
  (e/client
    (Block locus x)
    (when-some [locus (first router/route)]
      (router/pop
        (e/for [locus (e/diff-by identity (e/as-vec locus))] ; don't reuse DOM/IO frames across different objects
          (let [x (e/server (ex/Offload-reset #(nav-in x locus)))]
            (BrowsePath locus x)))))))

;; http://localhost:8080/datomic-browser.mbrainz-browser!DatomicBrowser/(:entity,17592186058336,'')/(:entity,580542139477874)/(:entity,17592186045645)
;; http://localhost:8080/datomic-browser.mbrainz-browser!DatomicBrowser/:entity/(17592186058336,'')/(580542139477874,'')/(17592186045645,'')

#_(case type
    :attributes (Attributes)
    :attribute (AttributeDetail)
    :tx-detail (TxDetail)
    :entity (e/amb (EntityDetail) #_(EntityHistory))
    :db-stats (DbStats)
    :recent-tx (RecentTx)
    (e/amb))

(e/defn EntityBlock []
  (EntityDetail)
  (r/pop
    (when-not (empty? r/route)
      (EntityBlock))))

(e/defn Page []
  (dom/style (dom/text css))
  (dom/props {:class "DatomicBrowser Explorer"})
  (when (empty? r/route) (r/ReplaceState! ['. [[:entity 17592186058336 ""]]]))
  (dom/props {:class (first r/route)}) ; bad/confusing, has to know route shape ahead of time.
  (Nav)
  (let [[page] r/route]
    (r/pop
      (case page
        :attributes (Attributes)
        :attribute (AttributeDetail)
        :tx-detail (TxDetail)
        :entity (let [[eid] r/route] (r/ReplaceState! ['.. [:entity-detail [:entity eid]]])) #_(e/amb (EntityBlock) #_(EntityHistory))
        :entity-detail (let [[_ eid] (first r/route)]
                         (r/link ['.. [:entity-history eid]] (dom/text "history"))
                         (EntityBlock))
        :entity-history (EntityHistory)
        :db-stats (DbStats)
        :recent-tx (RecentTx)
        (e/amb)))))

(e/defn DatomicBrowser2 [conn]
  (e/client
    (binding [db (e/server (e/Task (m/via m/blk (d/db conn))))
              conn conn]
      (Page))))

(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
  ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) Busy
    (or (some? (ex-message ?x)) (nil? ?x)) (Failed ?x)
    () (e/Partial Ok ?x)))

(e/defn Inject-datomic [datomic-uri F]
  (e/server
    (Inject (e/Task (m/via m/blk
                      (try (check (datomic.api/connect datomic-uri))
                           (catch Exception e #_(log/error e) e))))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                 (dom/pre (dom/text (pr-str err))))
       :Ok F})))

(def css "
/* Scroll machinery */
/* .Explorer { position: fixed; } */ /* mobile: don't allow momentum scrolling on page */
.Explorer .Viewport { overflow-x:hidden; overflow-y:auto; height: 480px; max-height: 100dvh; }
.Explorer table { display: grid; position:sticky; top:0; }
.Explorer table tr { display: contents; visibility: var(--visibility); }
.Explorer table td { grid-row: var(--order); }

/* Userland layout */
/* .Explorer fieldset { position:fixed; top:3em; bottom:0; left:0; right:0; } */
.Explorer table { grid-template-columns: 20em auto; }

/* Cosmetic */
.Explorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.Explorer legend { margin-left: 1em; font-size: larger; }
.Explorer legend > input[type=text] { vertical-align: middle; }
.Explorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.Explorer table td.reverse-attr { color: darkgray; font-style: italic; }
.Explorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.Explorer table tr:hover td { background-color: #ddd; }

/* Progressive enhancement */
.Explorer .nav { margin: 0; }
.Explorer:is(.entity,.entity-detail) fieldset .Viewport { height: 264px; } /* multiple of row height */
.Explorer:is(.entity,.entity-detail) fieldset table { grid-template-columns: 15em auto; }
.Explorer.attributes table { grid-template-columns: minmax(14em, 2fr) 1fr; }
.Explorer.attribute table { grid-template-columns: minmax(0, 1fr) 3fr minmax(0, 1fr); }
.Explorer.tx-detail table { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 2fr; }
.Explorer.recent-tx table { grid-template-columns: 10em auto; }
.Explorer.db-stats table { grid-template-columns: 20em auto; }
.Explorer :has(+ .entity-tooltip):hover + .entity-tooltip { visibility: visible; }
.Explorer .entity-tooltip {
  visibility: hidden;
  pointer-events: none;
  position: absolute;
  border: 1px whitesmoke solid;
  background-color: white;
  padding: 0.5rem 1rem;
  margin: 0;
  box-shadow: 0 0 1rem lightgray;
  border-radius: .25rem;
}
")
