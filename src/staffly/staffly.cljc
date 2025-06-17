(ns staffly.staffly
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric-forms5 :as forms]
            [dustingetz.loader :refer [Loader]]
            [staffly.staffly-model :as model]
            [staffly.staffly-index :refer [Index]]
            [staffly.staff-detail :refer [StaffDetail Change-phone-confirmed!]]
            [staffly.restrict-staff-from-venue :refer
             [RestrictStaffFromVenueForm Restrict-staff-from-venue!]]))

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:index]] (dom/text "home")) (dom/text " ")))

(e/defn Page []
  (dom/props {:class "staffly"})
  (dom/link (dom/props {:rel :stylesheet, :href "/user/staffly.css"}))
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:index]]))
    (r/pop
      (Nav)
      (case page
        :index (Index)
        :staff (StaffDetail)
        :restrict-staff-from-venue (RestrictStaffFromVenueForm)
        (dom/text "page not found")))))

(e/defn ConnectDatomic []
  (e/server
    (Loader #(missionary.core/? (model/init-datomic))
      {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
       :Failed (e/fn [error]
                 (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                 (dom/pre (dom/text (pr-str error))))})))

(e/defn Staffly
  ([] (Staffly (e/server (ConnectDatomic)))) ; wait until runtime to inject
  ([datomic-conn]
   (e/server
     datomic-conn ; force connect effect so *db* and *schema* get initialized.
     (bindx [model/datomic-conn (check datomic-conn) ; index page doesn't depend on conn, only *db*
             model/db (check model/*db*)
             model/schema (check model/*schema*)]
       (e/client
         (forms/Service {`Restrict-staff-from-venue! Restrict-staff-from-venue!
                         `Change-phone-confirmed! Change-phone-confirmed!}
           (Page)))))))

(e/defn Fiddles []
  {`Staffly Staffly
   `RestrictStaffFromVenueForm RestrictStaffFromVenueForm})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(Staffly)))
