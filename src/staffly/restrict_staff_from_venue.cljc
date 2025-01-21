(ns staffly.restrict-staff-from-venue
  (:require #?(:clj [datomic.api :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms4 :as forms :refer [Form! Input! Checkbox! Radio! FormStatus Button!]]
            [hyperfiddle.ui.typeahead2 :as t :refer [Typeahead!]]
            [hyperfiddle.router4 :as r]
            [staffly.staffly-model :as model]
            [clojure.string :as str]
            [hyperfiddle.ui.wizard2 :as wiz :refer [Wizard]]
            ))

#?(:clj (defn venue-picklist-query [db search]
          (->> (d/q '[:find [(pull ?e [:venue/id :venue/name]) ...] :where [?e :venue/id]] db)
            (filter #(str/includes? (:venue/name %) search)))))

#?(:clj (defn block-reasons-query [search]
          [{:db/ident :too-many-last-minute-cancels}
           {:db/ident :attire}
           {:db/ident :attitude}]))

(e/defn Venue-picklist [search-str] (e/server (e/diff-by :db/id (venue-picklist-query model/db search-str))))
(e/defn Block-reason-picklist [search-str] (e/server (e/diff-by :db/ident (block-reasons-query search-str))))

(e/defn ValidateForm ; form-level validation. Fields are individually validated.
  [{::keys [staff venue block-reason block-mode penalize] :as form}]
  (e/server
    ;; we check database for existing restriction for this staff at given venue.
    ;; UX improvement: gray out restricted venues options in picklist.
    (e/Offload #(when-let [existing-restriction (d/q '[:find ?reason .
                                                        :in $ ?staff ?venue-id
                                                        :where [?staff :staff/restrictions ?restriction]
                                                        [?restriction :restriction/venue ?venue-id]
                                                        [?restriction :restriction/reason ?reason]]
                                                  model/db staff [:venue/id (:venue/id venue)])]
                   (str "Staff already restricted from this venue: " existing-restriction)))))

#?(:clj ; business domain command
   (defn restrict-staff-from-venue! [conn staff venue-id block-reason block-mode penalize]
     @(d/transact conn [{:staff/_restrictions staff
                         :restriction/venue [:venue/id venue-id]
                         :restriction/reason block-reason
                         ;; :restriction/mode block-mode   ; might unlink existing gigs
                         ;; :restriction/penalize penalize ; ?
                         :restriction/scope :all-roles
                         :restriction/created-at (java.util.Date.)
                         :restriction/expires-at (.getTime (doto (java.util.Calendar/getInstance)
                                                             (.setTime (java.util.Date.))
                                                             (.add java.util.Calendar/MONTH 6)))}])))


(e/defn Restrict-staff-from-venue! [{::keys [staff venue block-reason block-mode penalize]}] ; FIXME not command-like, have to destructure object
  (e/server
    (e/Offload
      #(do (Thread/sleep 3000)
           (try (restrict-staff-from-venue! model/datomic-conn staff (:venue/id venue) (:db/ident block-reason) block-mode penalize)
                [::forms/ok]
                (catch Throwable t [::forms/rejected (ex-message t)]))))))

(e/defn Field [name value Control! & {::keys [label] :as props}]
  (let [control-id (random-uuid)]
    (dom/dt (dom/label (dom/props {:for control-id}) (dom/text label)))
    (dom/dd (Control! name value (-> (assoc props :id control-id) (dissoc ::label))))))

(e/defn RestrictStaffFromVenue-Step1 []
  (e/client
    (let [[e _] r/route, e (or e model/staff-sarah)]
      (dom/h1 (dom/text "Restrict staff from venue"))
      (dom/p (dom/text "Venue requested a given staff to be restricted from their venue."))
      (Form! `Restrict-staff-from-venue!
          (dom/dl
            (e/amb
              (Field ::staff e Input! :disabled true, :required true, ::label "staff")
              (Field ::venue nil Typeahead! ::label "venue", :required true, :Options Venue-picklist, :option-label :venue/name)
              (Field ::block-reason nil Typeahead! ::label "block-reason", :required true,
                :Options Block-reason-picklist
                :option-label #(some-> % :db/ident name))
              (Field ::penalize false Checkbox! ::label "penalize?", :label (constantly "Penalize the staff for any last-minute cancellations"))
              (Field ::block-mode nil Radio! ::label "block-mode", :required true,
                :Options (e/fn [] (e/amb :force-cancel :leave-commitments))
                :option-label #(case %
                                 :force-cancel "Cancel all the staff's existing commitment at this venue"
                                 :leave-commitments "Don't cancel staff's existing commitments")
                :Validate (e/fn [x] (when (nil? x) "Please pick one"))))) ; custom field-level validation
        :debug false ; true | false | :verbose
        #_#_:Validate ValidateForm))))

(e/defn WatchTx [form]
  (forms/FormStatus form (e/fn [status form err]
                           (dom/pre (dom/text status (::forms/name form))))))

(e/defn TrackFormTx [form]
  (forms/FormStatus form (e/fn [status form err]
                           (dom/div (dom/props {:style {:display :flex}})
                             (dom/span (dom/text (case status
                                                   ::forms/accepted "✅"
                                                   ::forms/rejected "❌"
                                                   "...")))
                             (dom/pre (dom/text (some-> (::forms/name form) name)))))))

(e/defn RestrictStaffFromVenueForm []
  (Wizard {:steps ["restrict" "notify"]}
    (e/fn [step [venue]]
      (case step
        "restrict" (-> (RestrictStaffFromVenue-Step1)
                     (TrackFormTx)
                     #_(forms/AfterSuccess (e/fn [{::keys [venue] :as form}] (forms/command ::wiz/next [(:venue/id venue)]))))
        "notify" (dom/p (dom/text "notify " venue))))))


;; (wiz/Stepper {:steps ["Name" "Address" "Recap"], :step step})