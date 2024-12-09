(ns staffly.staffly
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [datomic-browser.mbrainz-browser :refer [Inject]]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [staffly.staffly-model :as model]
            [staffly.staffly-index :refer [Index]]
            [staffly.staff-detail :refer [StaffDetail]]))

(e/declare *effects) ; to be bound to `{`Cmd-sym Cmd-efn}

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:index]] (dom/text "home")) (dom/text " ")))

(e/defn Page []
  (dom/props {:class ["rosie"
                      "mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 bg-white" ; responsive container
                      "flex flex-col gap-2" ; items in the page flow down, properly spaced and aligned
                      ]})
  (dom/link (dom/props {:rel :stylesheet, :href "/staffly.css"}))
  (dom/link (dom/props {:rel :stylesheet, :href "/gridsheet-optional.css"}))
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:index]]))
    (r/pop
      (Nav)
      (case page
        :index (Index)
        :staff (StaffDetail)
        (dom/text "page not found")))))

(e/defn Service [edits]
  (e/drain edits))

(e/defn Inject-datomic [F]
  (e/server
    (Inject (e/Task (model/init-datomic))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                 (dom/pre (dom/text (pr-str err))))
       :Ok F})))

(e/defn Staffly
  ([] (e/call (Inject-datomic Staffly))) ; wait until runtime to inject
  ([datomic-conn]
   (e/server
     (bindx [model/datomic-conn (check datomic-conn)
             model/db (check model/*db*)
             model/schema (check model/*schema*)
             *effects {}]
       (e/client
         (Service
           (Page)))))))

(e/defn Fiddles []
  {`Staffly Staffly})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(Staffly)))
