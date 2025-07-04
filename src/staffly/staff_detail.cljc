(ns staffly.staff-detail
  (:require #?(:clj [datomic.api :as d])
            [dustingetz.ui :refer [EasyForm TagPickerReadOnly]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Form! Checkbox! try-ok]]
            [hyperfiddle.router4 :as r]
            [staffly.staffly-model :as model]
            [staffly.utils :refer [Table]]))

#?(:clj (defn staff-detail [e]
          (let [pat [:staff/id
                     :staff/name
                     :staff/email
                     :staff/phone
                     :staff/notify-method
                     :staff/events-worked
                     :staff/venue-rating
                     :staff/punctuality-score
                     :staff/phone-confirmed
                     {:staff/roles ['*]}
                     {:staff/documents
                      [:document/id
                       :document/type
                       :document/name
                       :document/expiry
                       :document/status]}
                     {:staff/restrictions
                      [{:restriction/venue [:venue/id :venue/name]}
                       :restriction/reason
                       :restriction/scope
                       :restriction/created-at
                       :restriction/expires-at]}
                     {:staff/shifts
                      [{:shift/venue [:venue/id :venue/name]}
                       :shift/date
                       {:shift/role [:db/ident]}
                       :shift/rating
                       :shift/feedback]}]]
            (merge
              (d/pull model/*db* pat e)
              #_(suber-name-kv sub)
              ))))

(def links [:staff-shifts :staff-feedback :staff-restrictions :entity-history])
(def cmds [:restrict-staff-from-venue])

(def cols
  [:staff/id
   :staff/name
   :staff/email
   :staff/phone
   :staff/notify-method
   :staff/events-worked
   :staff/venue-rating
   :staff/phone-confirmed
   :staff/punctuality-score

   ; tables
   :staff/roles
   :staff/documents
   :staff/restrictions
   :staff/shifts])

(declare css)

(e/defn StaffDetail []
  (e/client
    (let [[e _] r/route, e (or e model/staff-sarah)]
      (dom/style (dom/text css))
      (dom/h1 (dom/text (str "Staff · " (pr-str e))))
      (dom/dl
        (dom/dt #_(dom/text "links"))
        (dom/dd
          (dom/nav
            (e/for [k (e/diff-by {} links)] (r/link ['.. [k e]] (dom/props {:disabled true, :tabindex "-1"}) (dom/text (name k)))) ; links
            (e/for [k (e/diff-by {} cmds)] (r/link ['.. [k e]] (dom/text (name k)))))) ; todo render commands as buttons
        (EasyForm
          (e/server (e/Offload #(staff-detail e)))
          cols
          {:staff/roles (e/fn [x]
                          (TagPickerReadOnly :db/ident
                            (mapv (partial hash-map :db/ident) x)))

           :staff/documents (e/fn [x]
                              (e/server
                                (Table "documents"
                                  [:document/type :document/name :document/expiry :document/status]
                                  (constantly x)
                                  (e/fn [a e]
                                    (let [v (a e)]
                                      (dom/td
                                        (dom/text
                                          (case a
                                            :document/expiry (e/client (some-> v .toLocaleDateString))
                                            v))))))))

           :staff/restrictions (e/fn [x]
                                 (e/server
                                   (Table "restrictions"
                                     [:restriction/venue :restriction/reason :restriction/scope :restriction/expires-at]
                                     (constantly x)
                                     (e/fn [a e]
                                       (let [v (a e)]
                                         (dom/td
                                           (dom/text
                                             (case a
                                               :restriction/expires-at (e/client (some-> v .toLocaleDateString))
                                               :restriction/venue (:venue/name v) v))))))))
           :staff/shifts (e/fn [x]
                           (e/server
                             (Table "shifts" ; :grid-template-columns "2fr 2fr 2fr 1fr"
                               [:shift/date :shift/venue :shift/role :shift/rating]
                               (constantly x)
                               (e/fn [a e]
                                 (let [v (a e)]
                                   (dom/td
                                     (dom/text
                                       (case a
                                         :shift/date (e/client (some-> v .toLocaleDateString))
                                         :shift/venue (:venue/name v)
                                         :shift/role (e/client (some-> v :db/ident name)) v))))))))

           :staff/phone-confirmed (e/fn [x] (Form! {} (e/fn [_]  (Checkbox! ::phone-confirmed? x))
                                              :Parse (e/fn [{::keys [phone-confirmed?]} _] [`Change-phone-confirmed! e phone-confirmed?])
                                              :show-buttons true))
           }))
        #_(Debug x))))

(e/defn Change-phone-confirmed! [e phone-confirmed?]
  (e/server
    (e/Offload-reset
      #(try-ok (throw (ex-info "Not implemented yet" {}))
         #_(change-phone-confirmed! e phone-confirmed?)))))


(def css (str hyperfiddle.electric-forms5/css
           staffly.utils/css
           "
.staffly .hyperfiddle-electric-forms5__table-picker  { --min-row-count: 5; }
.staffly .hyperfiddle-electric-forms5__table-picker table  { --column-count: 4; }

.staffly form:not(:has([aria-busy=true])) button {display: none;}
.staffly form:not(:has([aria-invalid=true])) [data-role=errormessage] {display: none;}
.staffly form [data-role=errormessage] {display: inline; margin: 0 1rem;}

"))