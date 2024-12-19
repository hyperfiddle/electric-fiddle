(ns staffly.staff-detail
  (:require #?(:clj [datomic.api :as d])
            [dustingetz.gridsheet3 :refer [Explorer3]]
            [dustingetz.ui :refer
             [Text EasyForm TagPickerReadOnly Debug]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer
             [Form! Checkbox!]]
            [hyperfiddle.router3 :as r]
            [staffly.staffly-model :as model]))

#?(:clj (defn staff-detail [e]
          (let [pat [:staff/id
                     :staff/name
                     :staff/email
                     :staff/phone
                     :staff/notify-method
                     :staff/events-worked
                     :staff/venue-rating
                     :staff/punctuality-score
                     {:staff/roles ['*]}
                     {:staff/documents
                      [:document/id
                       :document/type
                       :document/name
                       :document/expiry
                       :document/status]}
                     {:staff/restrictions
                      [:restriction/venue
                       :restriction/reason
                       :restriction/scope
                       :restriction/created-at
                       :restriction/expires-at]}
                     {:staff/shifts
                      [:shift/venue
                       :shift/date
                       {:shift/role [:db/ident]}
                       :shift/rating
                       :shift/feedback]}]]
            (merge
              (d/pull model/*db* pat e)
              #_(suber-name-kv sub)
              ))))

(comment (staff-detail model/staff-sarah))

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
   :staff/punctuality-score

   ; tables
   :staff/roles
   :staff/documents
   :staff/restrictions
   :staff/shifts])

(e/defn StaffDetail []
  (e/client
    (let [[e _] r/route, e (or e model/staff-sarah)]
      (dom/h1 (dom/text (str "Staff · " (pr-str e))))
      (dom/dl
        (dom/dt #_(dom/text "links"))
        (dom/dd
          (dom/nav
            (e/for [k (e/diff-by {} links)] (r/link ['.. [k e]] (dom/text (name k)))) ; links
            (e/for [k (e/diff-by {} cmds)] (r/link ['.. [k e]] (dom/text (name k)))))) ; todo render commands as buttons
        (EasyForm
          (e/server (e/Offload #(staff-detail e)))
          cols
          {:staff/roles (e/fn [x]
                          (TagPickerReadOnly :db/ident
                            (mapv (partial hash-map :db/ident) x)))

           :staff/documents (e/fn [x]
                              (Explorer3 x
                                :columns [:document/type :document/name :document/expiry :document/status]
                                :grid-template-columns "2fr 3fr 2fr 1fr"
                                :Format (e/fn [x a]
                                          (let [v (e/server (get x a nil))]
                                            (dom/text
                                              (case a
                                                :document/expiry (some-> v .toLocaleDateString)
                                                v))))))

           :staff/restrictions (e/fn [x]
                                 (Explorer3 x
                                   :columns [:restriction/venue :restriction/reason
                                             :restriction/scope :restriction/expires-at]
                                   :grid-template-columns "2fr 2fr 2fr 2fr"
                                   :page-size 8
                                   :Format (e/fn [x a]
                                             (let [v (e/server (get x a nil))]
                                               (dom/text
                                                 (case a
                                                   :restriction/expires-at (some-> v .toLocaleDateString)
                                                   :restriction/venue (:venue/name v)
                                                   v))))))

           :staff/shifts (e/fn [x]
                           (Explorer3 x
                             :columns [:shift/date :shift/venue :shift/role :shift/rating]
                             :grid-template-columns "2fr 2fr 2fr 1fr"
                             :page-size 8
                             :Format (e/fn [x a]
                                       (let [v (e/server (get x a nil))]
                                         (dom/text
                                           (case a
                                             :shift/date (some-> v .toLocaleDateString)
                                             :shift/venue (:venue/name v)
                                             :shift/role (some-> v :db/ident name)
                                             v))))))

           :sub/phone-confirmed (e/fn [x] (Form! (Checkbox! ::change-phone-confirmed x)
                                            :commit (fn [{::keys []}] [])
                                            :show-buttons :smart))
           }))
        #_(Debug x))))