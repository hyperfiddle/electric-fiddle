(ns staffly.restrict-staff-from-venue
  (:require #?(:clj [datomic.api :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Form! Input! Checkbox! Radio! FormStatus]]
            [hyperfiddle.ui.typeahead :as t :refer [Typeahead!]]
            [hyperfiddle.router4 :as r]
            [staffly.staffly-model :as model]
            [clojure.string :as str]))

#?(:clj (defn venue-picklist-query [db search]
          (->> (d/q '[:find [(pull ?e [:venue/id :venue/name]) ...] :where [?e :venue/id]] db)
            (filter #(str/includes? (:venue/name %) search)))))

#?(:clj (defn block-reasons-query [search]
          [{:db/ident :too-many-last-minute-cancels}
           {:db/ident :attire}
           {:db/ident :attitude}]))

(e/defn Venue-picklist [search-str] (e/server (e/diff-by :db/id (venue-picklist-query model/db search-str))))
(e/defn Block-reason-picklist [search-str] (e/server (e/diff-by :db/ident (block-reasons-query search-str))))

(e/defn ValidateFormCommand ; form-level validation, after mapping fields to command. Fields are individually validated.
  [[_command-identifier staff venue-id block-reason block-mode penalize :as command]]
  (e/server
    ;; we check database for existing restriction for this staff at given venue.
    ;; UX improvement: gray out restricted venues options in picklist.
    (e/Offload #(when-let [existing-restriction (d/q '[:find ?reason .
                                                        :in $ ?staff ?venue-id
                                                        :where [?staff :staff/restrictions ?restriction]
                                                        [?restriction :restriction/venue ?venue-id]
                                                        [?restriction :restriction/reason ?reason]]
                                                   model/db staff [:venue/id venue-id])]
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


(e/defn Restrict-staff-from-venue ; command
  [staff venue-id block-reason block-mode penalize]
  (e/server
    (e/Offload #(try (restrict-staff-from-venue! model/datomic-conn staff venue-id block-reason block-mode penalize)
                     :staffly.staffly/ok
                     (catch Throwable t
                       (ex-message t))))))

(defmacro field ; markup/cosmetic only
  [Control! name value & {::keys [label] :as props}]
  (let [control-id (random-uuid)]
    `(e/amb
       (dom/dt (dom/label (dom/props {:for ~control-id}) (dom/text ~label)))
       (dom/dd (e/call ~Control! ~name ~value (-> (assoc ~props :id ~control-id) (dissoc ::label)))))))

(e/defn RestrictStaffFromVenueForm []
  (e/client
    (let [[e _] r/route, e (or e model/staff-sarah)]
      (dom/h1 (dom/text "Restrict staff from venue"))
      (dom/p (dom/text "Venue requested a given staff to be restricted from their venue."))
      (Form!
          (dom/dl
            (e/amb
              (field Input! ::staff, e, :disabled true, :required true, ::label "staff")
              (field Typeahead! ::venue, nil, ::label "venue", :required true, :Options Venue-picklist, :option-label :venue/name)
              (field Typeahead! ::block-reason, nil, ::label "block-reason", :required true, :Options Block-reason-picklist, :option-label #(some-> % :db/ident name))
              (field Checkbox! ::penalize, false, ::label "penalize?", :label (constantly "Penalize the staff for any last-minute cancellations"))
              (field Radio! ::block-mode, nil, ::label "block-mode", :required true,
                     :Options (e/fn [] (e/amb :force-cancel :leave-commitments)),
                     :option-label #(case % :force-cancel      "Cancel all the staff's existing commitment at this venue"
                                          :leave-commitments "Don't cancel staff's existing commitments"),
                     :Validate (e/fn [x] (when (nil? x) "Please pick one")) ; custom field-level validation
                     )))
        :debug false ; true | false | :verbose
        :commit ; map fields to a command
        (fn [{::keys [venue block-reason block-mode penalize]}] ; fields addressed by name
          [[`Restrict-staff-from-venue e (:venue/id venue) (:db/ident block-reason) block-mode penalize]]) ; command as a value, to be interpreted later
        :Validate ValidateFormCommand
        :Accepted (e/fn [] ; Redirect to entity page 2s after accepted transaction
                    (r/Navigate! ['.. [:staff e]] 2000))))))
