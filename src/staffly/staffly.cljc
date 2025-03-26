(ns staffly.staffly
  (:require [contrib.assert :refer [check]]
            [contrib.clojurex :refer [bindx]]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric-forms5 :as forms]
            [staffly.staffly-model :as model]
            [staffly.staffly-index :refer [Index]]
            [staffly.staff-detail :refer [StaffDetail Change-phone-confirmed!]]
            [staffly.restrict-staff-from-venue :refer
             [RestrictStaffFromVenueForm Restrict-staff-from-venue!]]))

(e/defn Nav []
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:index]] (dom/text "home")) (dom/text " ")))

(e/defn Page []
  (dom/props {:class "staffly "})
  #_(dom/link (dom/props {:rel :stylesheet, :href "/staffly.css"}))
  (dom/link (dom/props {:rel :stylesheet, :href "/gridsheet-optional.css"}))
  (let [[page] r/route]
    (when-not page (r/ReplaceState! ['. [:index]]))
    (r/pop
      (Nav)
      (case page
        :index (Index)
        :staff (StaffDetail)
        :restrict-staff-from-venue (RestrictStaffFromVenueForm)
        (dom/text "page not found")))))

(e/defn Inject [?x #_& {:keys [Busy Failed Ok]}]
  ; todo needs to be a lot more sophisticated to inject many dependencies concurrently and report status in batch
  (cond
    (ex/None? ?x) (Busy)
    (or (some? (ex-message ?x)) (nil? ?x)) (Failed ?x)
    () (Ok ?x)))

(e/defn Inject-datomic [F]
  (e/fn []
    (e/server
      (Inject (e/Task (model/init-datomic))
        {:Busy (e/fn [] (dom/h1 (dom/text "Waiting for Datomic connection ...")))
         :Failed (e/fn [err] (dom/h1 (dom/text "Datomic transactor not found, see Readme.md"))
                   (dom/pre (dom/text (pr-str err))))
         :Ok F}))))

(e/defn Staffly
  ([] (e/call (Inject-datomic Staffly))) ; wait until runtime to inject
  ([datomic-conn]
   (e/server
     (bindx [model/datomic-conn (check datomic-conn)
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
