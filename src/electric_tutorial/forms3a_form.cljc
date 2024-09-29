(ns electric-tutorial.forms3a-form
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service PendingController]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]))

#?(:clj (def !conn))

(e/defn Query-record [db id edits]
  (e/client
    #_(PendingController1 nil nil edits)
    #_(Service edits) ; rebind transact to with, db must escape
    (e/server (e/Offload #(d/pull db [:user/str1 :user/num1 :user/bool1] id)))))

(e/defn UserForm [db id edits]
  (dom/fieldset (dom/legend (dom/text "transactional form"))
    (let [{:keys [user/str1 user/num1 user/bool1] :as m} (Query-record db id edits)]
      (Form ; buffer and batch edits into an atomic form
        (dom/dl
          (e/amb ; concurrent field edits (which get us field dirty state).
            ; if we used e.g. a vector/map aggregator like before, we'd need
            ; circuit controls and therefore lose field dirty state.
            (dom/dt (dom/text "str1")) (dom/dd (Input! :user/str1 str1))
            (dom/dt (dom/text "num1")) (dom/dd (Input! :user/num1 num1 :type "number" :parse parse-long))
            (dom/dt (dom/text "bool1")) (dom/dd (Checkbox! :user/bool1 bool1))))
        :commit (fn [dirty-form]
                  (let [{:keys [user/str1 user/num1 user/bool1] :as m} (merge m dirty-form)]
                    [[`UserFormSubmit id str1 num1 bool1] {id m}]))
        :debug true))))

(e/defn UserFormSubmit [id str1 num1 bool1]
  #_(e/server (prn 'UserFormSubmit id str1 num1 bool1))
  (e/server ; secure command interpretation, validate command here
    (let [tx [{:db/id id :user/str1 str1 :user/num1 num1 :user/bool1 bool1}]]
      (e/Offload #(try (d/transact! !conn tx) (doto ::cqrs/ok (prn 'UserFormSubmit))
                    (catch Exception e (doto ::fail (prn 'UserFormSubmit e))))))))

(e/defn Forms3a-form []
  (binding [cqrs/*effects* {`UserFormSubmit UserFormSubmit}]
    (let [db (e/server (e/watch !conn))]
      (Service
        (e/with-cycle* first [edits (e/amb)]
          (UserForm db 42 edits))))))