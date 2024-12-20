(ns staffly.restrict-staff-from-venue
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

(e/defn ValidateFormCommand ; form-level validation, after mapping fields to command. Fields are individually validated.
  [[_command-identifier staff venue-id block-reason block-mode penalize :as command]]
  (e/server
    (e/Offload #(do (Thread/sleep 500) ; simulate remote validation latency
                    (when (= 1 venue-id)
                      "Staff already blocked from this venue: too-many-last-minute-cancels.")))))

(defmacro field ; markup/cosmetic only
  [Control! name value & {::keys [label] :as props}]
  (let [control-id (random-uuid)]
    `(e/amb
       (dom/dt (dom/label (dom/props {:for ~control-id}) (dom/text ~label)))
       (dom/dd (e/call ~Control! ~name ~value (-> (assoc ~props :id ~control-id) (dissoc ::label)))))))

(e/defn RestrictStaffFromVenue []
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
          [`Restrict-staff-from-venue e (:venue/id venue) (:db/ident block-reason) block-mode penalize]) ; command as a value, to be interpreted later
        :Validate ValidateFormCommand))))