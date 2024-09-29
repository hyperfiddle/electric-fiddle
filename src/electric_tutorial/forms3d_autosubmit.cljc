(ns electric-tutorial.forms3d-autosubmit
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3a-form :refer [Query-record #?(:clj !conn)]]
            [electric-tutorial.forms3b-inline-submit :refer
             [Str1FormSubmit Num1FormSubmit Bool1FormSubmit]]))

(e/defn UserForm [db id edits]
  (dom/fieldset (dom/legend (dom/text "transactional fields with autosubmit"))
    (let [{:keys [user/str1 user/num1 user/bool1]} (Query-record db id edits)]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form (Input! :user/str1 str1)
                    :commit (fn [{v :user/str1}]
                              [[`Str1FormSubmit id v] {id {:user/str1 v}}])
                    :auto-submit true :show-buttons false))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form (Input! :user/num1 num1 :type "number" :parse parse-long)
                    :commit (fn [{v :user/num1}]
                              [[`Num1FormSubmit id v] {id {:user/num1 v}}])
                    :auto-submit true :show-buttons false))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form (Checkbox! :user/bool1 bool1)
                    :commit (fn [{v :user/bool1}]
                              [[`Bool1FormSubmit id v] {id {:user/bool1 v}}])
                    :auto-submit true :show-buttons false)))))))

(e/defn Forms3d-autosubmit []
  (binding [cqrs/*effects* {`Str1FormSubmit Str1FormSubmit
                            `Num1FormSubmit Num1FormSubmit
                            `Bool1FormSubmit Bool1FormSubmit}]
    (let [db (e/server (e/watch !conn))]
      (Service
        (e/with-cycle* first [edits (e/amb)]
          (UserForm db 42 edits))))))