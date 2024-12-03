(ns staffly.fiddles
  (:require [contrib.assert :refer [check]]
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [staffly.staffly-model :as model]
            [staffly.staffly-index :refer [Index]]
            [staffly.staff-detail :refer [StaffDetail]]))

(def *effects {}) ; to be bound to `{`Cmd-sym Cmd-efn}

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:index]] (dom/text "home")) (dom/text " ")))

(e/defn Page []
  (dom/props {:class ["rosie"
                      "mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 bg-white" ; responsive container
                      "flex flex-col gap-2" ; items in the page flow down, properly spaced and aligned
                      ]})
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

(e/defn Inject-datomic [Page]
  (e/server
    (let [x (e/Task (model/init-datomic) ::pending)]
      (case x
        ::pending (dom/h1 (dom/text "Waiting for Datomic connection ..."))
        ::model/ok (binding [model/datomic-conn (check model/*datomic-conn*)
                             model/db (check model/*db*)
                             model/schema (check model/*schema*)]
                     (e/client
                       (Page)))
        (do (dom/h1 (dom/text "Datomic transactor not found"))
            (dom/code (dom/text x)))))))

(e/defn Staffly []
  (Inject-datomic
    (e/fn []
      (binding [*effects {}]
        (Service
          (Page))))))

(e/defn Fiddles []
  {`Staffly Staffly})

(e/defn FiddleMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)
              pages (Fiddles)]
      (dom/div ; mandatory wrapper div - https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (Staffly))))))