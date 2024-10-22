(ns electric-tutorial.forms3a-form
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form! Service try-ok PendingController]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

(e/defn Query-record [db id forms]
  (e/client
    #_(PendingController1 nil nil forms)
    (e/server (e/Offload #(d/pull db [:user/str1 :user/num1 :user/bool1] id)))))

(e/defn UserForm [db id forms]
  (dom/fieldset (dom/legend (dom/text "transactional form"))
    (let [{:keys [user/str1 user/num1 user/bool1] :as m} (Query-record db id forms)]
      (Form! ; buffer and batch edits into an atomic form
        (dom/dl
          (e/amb
            (dom/dt (dom/text "str1")) (dom/dd (Input! :user/str1 str1))
            (dom/dt (dom/text "num1")) (dom/dd (Input! :user/num1 num1 :type "number" :parse parse-long))
            (dom/dt (dom/text "bool1")) (dom/dd (Checkbox! :user/bool1 bool1))))
        :commit (fn [dirty-form]
                  (let [{:keys [user/str1 user/num1 user/bool1] :as m} (merge m dirty-form)]
                    [[`UserFormSubmit id str1 num1 bool1] {id m}]))
        :debug true))))

#?(:clj (defn transact-unreliable [!conn tx & {:keys [die] :or {die false}}]
          (cond
            (true? die) (throw (ex-info "die" {}))
            () (d/transact! !conn tx))))

(e/defn UserFormSubmit [id str1 num1 bool1]
  #_(e/server (prn 'UserFormSubmit id str1 num1 bool1))
  (e/server ; secure command interpretation, validate command here
    (let [tx [{:db/id id :user/str1 str1 :user/num1 num1 :user/bool1 bool1}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx))))))

(e/defn Forms3a-form []
  (e/client
    (binding [cqrs/effects* {`UserFormSubmit UserFormSubmit}]
      (let [db (e/server (e/watch !conn))]
        (Service
          (e/with-cycle* first [forms (e/amb)]
            (UserForm db 42 forms)))))))

