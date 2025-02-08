(ns datomic-browser.dbob
  (:require [dustingetz.entity-browser2 :as eb]
            [contrib.assert :as ca]
            [contrib.data :as cd]
            #?(:clj dustingetz.datomic-contrib) ; datafy entity
            [clojure.string :as str]
            [contrib.str :as cstr]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric3-contrib :as ex]
            #?(:clj [datomic.api :as d])
            #?(:clj [datomic-browser.datomic-model :as da-model])
            [missionary.core :as m]))

(e/declare conn)
(e/declare db)

(def attributes-colspec
  [:db/ident :db/unique :db/isComponent
   {:db/valueType [:db/ident]}
   {:db/cardinality [:db/ident]}
   #_#_#_#_:db/fulltext :db/tupleType :db/tupleTypes :db/tupleAttrs])

(e/defn Attributes []
  (e/server (mapv #(d/entity db %) (d/q '[:find [?e ...] :in $ :where [?e :db/valueType]] db))))

(e/defn UserResolve [tag]
  (e/server
    (case tag
      :attributes (Attributes)
      (e/amb))))

(def targets [[:attributes]])

(declare css)

(e/defn DatomicBrowserOB
  ([conn]
   (e/client
     (binding [db (e/server (e/Task (m/via m/blk (d/db conn))))
               conn conn]
       (dom/style (dom/text css))
       (dom/props {:class "ThreadDump3"})
       (dom/text "Target: ")
       (e/for [[tag :as ref] (e/diff-by {} targets)]
         (prn 'tag tag 'ref ref)
         (r/link ['. ref] (dom/text (pr-str (remove nil? [tag])))))
       (when-some [[uri & _] r/route]
         (r/pop
           (e/for [uri (e/diff-by identity (e/as-vec uri))] ; workaround glitch (Leo, please explain?)
             (eb/EntityBrowser2 (e/server (cd/map-entry uri (UserResolve uri)))))))))))

(def css "
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")
