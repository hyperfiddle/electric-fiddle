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
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [i (IndexRing limit offset)]
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              (Row (nth (vec ?xs!) i nil)))))
        (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                    (* row-height (- record-count limit)) 0) "px")}}))))))

(e/declare conn)
(e/declare db)

(e/defn SearchGrid [title Query Row]
  (let [search (e/client (r/pop (first r/route)))
        xs! (e/server (Query search)) ; wtf
        n (e/server (count xs!))]
    (dom/fieldset
      (dom/legend (dom/text title " ")
                  (e/client (r/pop (r/ReplaceState! ['. [(Input* search)]])))
                  (dom/text " (" n " items)"))
      (TableScroll n xs! Row))))

(def attributes-colspec
  [:db/ident :db/unique :db/isComponent
   {:db/valueType [:db/ident]}
   {:db/cardinality [:db/ident]}
   #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs])

(e/defn Attributes []
  (SearchGrid "Attributes"
    (e/fn Query [search]
      (e/server (->> (attributes-stream db attributes-colspec) (m/reduce conj []) e/Task
                  (map #(assoc % ::summary (->> (summarize-attr db (:db/ident %)) (map name) (clojure.string/join " "))))
                  (filter #(includes-str? ((juxt :db/ident ::summary) %) search)) ; search after summary
                  (sort-by :db/ident))))
    (e/fn Row [x]
      (dom/td (let [v (:db/ident x)] (r/link ['.. [:attribute v]] (dom/text v))))
      (dom/td (dom/text (::summary x))))))

(e/defn AttributeDetail []
  (let [[a _] r/route]
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
              (dom/td (r/link ['.. [:tx-detail tx]] (dom/text tx))))))))))

(e/defn TxDetail []
  (let [[e _] r/route]
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
        ))))

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

(e/defn Format-entity [e {:keys [path name value] :as ?row}]
  (e/server ; keep vals on server, row can contain refs
    (let [k name v value]
      (when ?row
        (dom/td (dom/props {:style {:padding-left (some-> path count (* 15) (str "px"))}})
          (cond
            (= :db/id k) (dom/text k) ; :db/id is our schema extension, can't nav to it
            (is-attr? db (absolute-attribute k)) 
            (if (reverse-attribute? k) 
              (r/link ['.. [:attribute (absolute-attribute k) e]] (dom/text k))
              (r/link ['.. [:attribute (absolute-attribute k)]] (dom/text k)))
            () (dom/text (str k)))) ; str is needed for Long db/id, why?
        (dom/td
          (if-not (coll? v) ; don't render card :many intermediate row
            (let [[valueType cardinality] (easy-attr db k)]
              (cond
                (= :db/id k) (r/link ['.. '.. [:entity v]] (dom/text v))
                (= :ref valueType) (r/link ['.. '.. [:entity v]] (dom/text v))
                (= :string valueType) (dom/text v) #_(Form! (Input! k v) :commit (fn [] []) :show-buttons :smart)
                () (dom/text (str v))))
            (cond
              (reverse-attribute? k) (do (dom/props {:class "reverse-attr"})
                                         (dom/text (cl-format false "~d reference~:p" (count v))))
              () (e/amb))))))))

(letfn [(attribute-name [attribute] (if (reverse-attribute? attribute) (namespace attribute) (name attribute)))]
  (defn sort-by-attr [entity-like-map]
    (into (ordered-map)
      (sort-by (comp attribute-name key) entity-like-map))))

(e/defn EntityDetail []
  (let [[e _] r/route]
    #_(r/focus [1]) ; search
    (when e ; glitch
      (SearchGrid (str "Entity detail: " e)
        (e/fn Query [search]
          (e/server
            (->> (flatten-nested (e/Task (m/via m/blk (sort-by-attr (merge (d/pull db ['*] e) (contrib.datomic-contrib/back-references db e)))))) ; TODO render backrefs at the end?
              (filter #(includes-str? (str ((juxt :name :value) %)) search))))) ; string the entries
        (e/Partial Format-entity e)))))

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
  (let [[e _] r/route]
    #_(r/focus [1]) ; search
    (when e ; router glitch
      (SearchGrid (str "Entity history: " e)
        (e/fn [search]
          (e/server (->> (entity-history-datoms-stream db e) (m/reduce conj []) e/Task
                      (filter #(includes-str? (str ((juxt :e #_:a :v #_:tx) %)) search))))) ; todo resolve human attrs
        Format-history-row
        #_{:columns [::e ::a ::op ::v ::tx-instant ::tx]
           ::dom/class "Viewport entity-history"}))))

(e/defn DbStats []
  #_(r/focus [0]) ; search
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
                  () (dom/text (pr-str v))))))))

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
  (SearchGrid (str "Recent Txs")
    (e/fn Query [search]
      (e/server (->> (d/datoms db :aevt :db/txInstant) seq-consumer (m/reduce conj ()) e/Task
                  (filter #(includes-str? (str ((juxt :e #_:a :v #_:tx) %)) search)))))
    (e/fn Row [[e _ v tx op :as ?row]]
      ; columns [:db/id :db/txInstant]
      (when ?row
        (dom/td (e/client (r/link ['.. [:tx-detail tx]] (dom/text tx))))
        (dom/td (dom/text (e/client (pr-str v)) #_(e/client (.toLocaleDateString v))))))))

(e/defn Nav []
  (dom/div (dom/props {:class "nav"})
    (dom/text "Nav: ")
    (r/link ['.. [:attributes]] (dom/text "attributes")) (dom/text " ")
    (r/link ['.. [:db-stats]] (dom/text "db-stats")) (dom/text " ")
    (r/link ['.. [:recent-tx]] (dom/text "recent-tx"))
    (dom/text " — Datomic Browser2")))

(declare css)
(e/defn Page []
  (dom/style (dom/text css))
  (dom/props {:class "DatomicBrowser Explorer"})
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:attributes]]))
    (dom/props {:class page})
    (r/pop
      (Nav)
      (case page
        :attributes (Attributes)
        :attribute (AttributeDetail)
        :tx-detail (TxDetail)
        :entity (e/amb (EntityDetail) (EntityHistory))
        :db-stats (DbStats)
        :recent-tx (RecentTx)
        (e/amb)))))

(e/defn DatomicBrowser [conn]
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
.Explorer { position: fixed; } /* mobile: don't allow momentum scrolling on page */
.Explorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.Explorer table { display: grid; }
.Explorer table tr { display: contents; visibility: var(--visibility); }
.Explorer table td { grid-row: var(--order); }
.Explorer div.Viewport { height: 100%; }

/* Userland layout */
.Explorer fieldset { position:fixed; top:3em; bottom:0; left:0; right:0; }
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
.Explorer.entity fieldset:nth-of-type(1) { top:3em; bottom:40vh; left:0; right:0; }
.Explorer.entity fieldset:nth-of-type(2) { top:60vh; bottom:0; left:0; right:0; }
.Explorer.entity fieldset:nth-of-type(1) table { grid-template-columns: 15em auto; }
.Explorer.entity fieldset:nth-of-type(2) table { grid-template-columns: 5em 10em 15em auto 10em 9em; }
.Explorer.attributes table { grid-template-columns: minmax(14em, 2fr) 1fr; }
.Explorer.attribute table { grid-template-columns: minmax(0, 1fr) 3fr minmax(0, 1fr); }
.Explorer.tx-detail table { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 2fr; }
.Explorer.recent-tx table { grid-template-columns: 10em auto; }
.Explorer.db-stats table { grid-template-columns: 20em auto; }
")