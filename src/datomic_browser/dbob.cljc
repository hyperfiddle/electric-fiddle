(ns datomic-browser.dbob
  (:require [dustingetz.entity-browser2 :as eb]
            #?(:clj dustingetz.mbrainz)
            #?(:clj dustingetz.identify)
            [contrib.assert :as ca]
            [contrib.data :as cd]
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

#?(:clj
   (extend-type datomic.query.EntityMap
     dustingetz.identify/Identifiable
     (-identify [^datomic.query.EntityMap !e] (:db/id !e))))

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

(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
  ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) Busy
    (or (some? (ex-message ?x)) (nil? ?x)) (Failed ?x)
    () (e/Partial Ok ?x)))

(e/defn Inject-datomic [datomic-uri F]
  (e/server
    (Inject (e/Task (m/via m/blk
                      (try (ca/check (datomic.api/connect datomic-uri))
                           (catch Exception e #_(log/error e) e))))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                 (dom/pre (dom/text (pr-str err))))
       :Ok F})))

(declare css)

(e/defn DatomicBrowserOB
  ([] (e/call (Inject-datomic dustingetz.mbrainz/mbrainz-uri DatomicBrowserOB)))
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
