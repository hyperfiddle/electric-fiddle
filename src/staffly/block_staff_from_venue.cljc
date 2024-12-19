(ns staffly.block-staff-from-venue
  (:require #?(:clj [datomic.api :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Form! Input! Checkbox! Radio!]]
            [hyperfiddle.ui.typeahead :as t :refer [Typeahead!]]
            [hyperfiddle.router3 :as r]
            [staffly.staffly-model :as model]))

#?(:clj (defn venue-picklist-query [search]
          [{:venue/id 1, :venue/name "Venue1"}
           {:venue/id 2, :venue/name "Venue2"}
           {:venue/id 3, :venue/name "Venue3"}]))

#?(:clj (defn block-reasons-query [search]
          [{:db/ident :too-many-last-minute-cancels}
           {:db/ident :attire}
           {:db/ident :attitude}]))

(e/defn Venue-picklist [search-str] (e/server (e/diff-by :db/id (venue-picklist-query search-str))))
(e/defn Block-reason-picklist [search-str] (e/server (e/diff-by :db/ident (block-reasons-query search-str))))

(e/defn ValidateForm [[_cmd staff venue-id block-reason block-mode penalize :as cmd]]
  (e/server
    (e/Offload #(do (Thread/sleep 500)
                    (when (= 1 venue-id)
                      "Staff already blocked from this venue: too-many-last-minute-cancels.")))))

(defmacro field ; markup/cosmetic only
  [Control! name value & {::keys [label] :as props}]
  (let [control-id (random-uuid)
        validation-message-id (random-uuid)]
    `(e/amb
       (dom/dt
         (dom/label (dom/props {:for ~control-id}) (dom/text ~label)))
       (dom/dd
         (let [[~'_ ~'_ validation-message# :as edit#]
               (e/call ~Control! ~name ~value (-> (assoc ~props :id ~control-id, :aria-errormessage ~validation-message-id)
                                                (dissoc ::label)))]
           #_(dom/p (dom/props {:id ~validation-message-id, :data-role "errormessage"}) ; no native errormessage role
             (e/When validation-message# ; dom/text doesn't unmount on Value -> amb
               (dom/text validation-message#)))
           edit#)))))

(e/defn BlockStaffFromVenue []
  (e/client
    (let [[e _] r/route, e (or e model/staff-sarah)]
      (dom/h1 (dom/text "block staff from venue"))
      (dom/p (dom/text "Venue request a given staff to be restricted from their venue."))
      (Form!
        (dom/dl
          (e/amb
            (field Input! ::staff e :disabled true :required true :Validate (e/fn [staff] (when-not (= "12345" staff) "Wrong staff")), ::label "staff") 
            (field Typeahead! ::venue nil :Options Venue-picklist :option-label :venue/name, :required true, ::label "venue" #_#_:open? true)
            (field Typeahead! ::block-reason nil :Options Block-reason-picklist :option-label #(some-> % :db/ident name), :required true, ::label "block-reason")
            (field Checkbox! ::penalize false ::label "penalize?", :label (constantly "Penalize the staff for any last-minute cancellations"))
            (field Radio! ::block-mode nil :required true ::label "block-mode" :Options (e/fn [] (e/amb :force-cancel :leave-commitments))
              :option-label #(case % :force-cancel "Cancel all the staff's existing commitment at this venue"
                                   :leave-commitments "Don't cancel staff's existing commitments")
              :Validate (e/fn [x] (when (nil? x) "Please pick one")))))

        :debug :verbose
        :Validate ValidateForm
        :commit (fn [{::keys [venue block-reason block-mode penalize] :as state}]
                  [`Block-staff-from-venue e (:venue/id venue) (:db/ident block-reason) block-mode penalize]) ; just a command, no guess
        )
 
      #_(Debug x))))