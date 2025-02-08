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
            (mapv #(d/entity db %)))))

(e/defn Attributes []
  (e/client
    (TableBlock ::select-user
      (e/server (map-entry `Attributes #_(attributes db)
                  (->> (attributes-stream db [:db/ident]) (m/reduce conj []) e/Task (sort-by :db/ident)
                    (mapv #(assoc % ::summary (->> (summarize-attr db (:db/ident %)) (map name) (clojure.string/join " ")))))))
      nil :cols *hfql-spec)))

(e/defn DbStats []
  (e/client
    (TreeBlock ::select-user
      (e/server (map-entry `DbStats (d/db-stats db)))
      nil :cols *hfql-spec)))

(e/defn AttributeDetail []
  (e/client
    (let [[a _] r/route]
      (TreeBlock ::select-user
        (e/server (when a ; glitch
                    (map-entry `AttributeDetail
                      (->> (seq-consumer (d/datoms db :aevt a)) (m/reduce conj []) e/Task (sort-by :v)))))
        nil :cols *hfql-spec))))

(declare sitemap)

(e/defn Index []
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Target: ")
    (e/for [page-sym (e/amb `Index `Attributes `DbStats)]
      (r/link ['.. [page-sym]] (dom/text (name page-sym))))))

(def sitemap
  {`Index nil
   `Attributes [:db/ident ::summary]
   `DbStats ['*]
   `AttributeDetail ['*]})

(declare css)
(e/defn Fiddles []
  {`DatomicBrowserOB (Inject-datomic dustingetz.mbrainz/mbrainz-uri
                       (e/fn [conn]
                         (binding [pages {`Index Index
                                          `Attributes Attributes
                                          `AttributeDetail AttributeDetail
                                          `DbStats DbStats}
                                   db (e/server (ex/Offload-latch #(d/db conn)))]
                           (dom/style (dom/text css))
                           (HfqlRoot sitemap :default `(Index)))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")
