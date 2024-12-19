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
            [staffly.staff-detail :refer [StaffDetail]]
            [staffly.block-staff-from-venue :refer [BlockStaffFromVenue]]))

(e/declare *effects) ; to be bound to `{`Cmd-sym Cmd-efn}

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
        :restrict-staff-from-venue (BlockStaffFromVenue)
        (dom/text "page not found")))))

#_(e/defn Service [edits]
  (e/drain edits))

(e/defn Service [edits]
  (e/client
    (let [fail false #_(dom/div (dom/props {:class "fixed bottom-0"}) (Checkbox* true :label "failure"))
          edits (e/Filter some? edits)]
      (println 'edits (e/Count edits) (e/as-vec edits))
      (e/for [[t [cmd & args] guess] edits]
        (let [Effect (get *effects cmd (e/fn [& args] (e/server (e/Offload #(do (Thread/sleep 500) (doto ::ok (prn cmd)))))))
              res (e/server (if fail (e/Offload #(do (Thread/sleep 2000) ::rejected)) (e/Apply Effect args)))]
          (prn "tx" res)
          (case res
            nil (prn 'res-was-nil-stop!)
            ::ok (t)
            (t res)))))))

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
  {`Staffly Staffly
   `BlockStaffFromVenue BlockStaffFromVenue})

(e/defn ProdMain [ring-req]
  (FiddleMain ring-req (Fiddles)
    :default `(Staffly)))
