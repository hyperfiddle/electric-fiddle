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
            [missionary.core :as m]))

(e/declare conn)
(e/declare db)

#?(:clj (defn attributes [db]
          (->> (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db)
            (mapv #(d/entity db %))
            (sort-by :db/ident))))

(e/defn Attributes []
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `Attributes (attributes db)))
      nil *hfql-spec
      #_#_:Row (e/fn Row [cols x]
             (e/server
               (dom/td (let [v (:db/ident x)] (r/link ['.. [`AttributeDetail v]] (dom/text v))))
               (dom/td (dom/text (::summary x))))))))

(e/defn DbStats []
  (e/client
    (TreeBlock ::select-user
      (e/server (map-entry `DbStats (d/db-stats db)))
      nil :cols *hfql-spec)))

(e/defn AttributeDetail [a]
  (e/client
    (TableBlock ::select-user
      (e/server (when a ; glitch
                  (map-entry `AttributeDetail
                    (->> (seq-consumer (d/datoms db :aevt a)) (m/reduce conj []) e/Task (sort-by :v)))))
      nil :cols *hfql-spec
      :Row (e/fn [cols x]
             (e/server
               (let [[e _ v tx op] x]
                 (dom/td (r/link ['.. [`EntityDetail e]] (dom/text e)))
                 (dom/td (some-> v str dom/text)) ; todo when a is ref, render link
                 (dom/td (r/link ['.. [`TxDetail tx]] (dom/text tx)))))))))

(e/defn EntityDetail [])

(e/defn TxDetail [])

(e/defn Index []
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Target: ")
    (e/for [page-sym (e/amb `Index `Attributes `DbStats)]
      (r/link ['.. [page-sym]] (dom/text (name page-sym))))))

#?(:clj (defn summarize-attr* [?!a]
          (let [db @dustingetz.mbrainz/test-db] ; todo hfql binding conveyance
            (when ?!a (->> (easy-attr db (:db/ident ?!a)) (remove nil?) (map name) (clojure.string/join " "))))))

#?(:clj (def !sitemap
          (atom ; picker routes should merge into colspec as pull recursion
            {`Index nil
             `Attributes [:db/ident `(summarize-attr* ~'%) #_'*]
             `DbStats ['*]
             `AttributeDetail ['*] ; #datom
             `TxDetail ['*]
             `EntityDetail ['*]})))

(comment
  (swap! !sitemap update-in [`Attributes] conj :db/id)
  (swap! !sitemap update-in [`Attributes] (constantly [:db/ident]))
  (swap! !sitemap update-in [`Attributes] (constantly [:db/ident `(summarize-attr* ~'%)]))
  )

(declare css)
(e/defn Fiddles []
  {`DatomicBrowserOB (Inject-datomic dustingetz.mbrainz/mbrainz-uri
                       (e/fn [conn]
                         (binding [pages {`Index Index
                                          `Attributes Attributes
                                          `AttributeDetail AttributeDetail
                                          `DbStats DbStats
                                          `TxDetail TxDetail
                                          `EntityDetail EntityDetail}
                                   db (e/server (ex/Offload-latch #(d/db conn)))]
                           (dom/style (dom/text css))
                           (HfqlRoot (e/server (e/watch !sitemap)) :default `(Index)))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")
