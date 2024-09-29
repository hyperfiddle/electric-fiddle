(ns electric-tutorial.forms3b-inline-submit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form! Service]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3a-form :refer [Query-record #?(:clj !conn)]]))

(e/defn UserForm [db id edits]
  (dom/fieldset (dom/legend (dom/text "transactional fields with inline submit"))
    (let [{:keys [user/str1 user/num1 user/bool1]} (Query-record db id edits)]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form! (Input! :user/str1 str1)
                    :commit (fn [{v :user/str1}]
                              [[`Str1FormSubmit id v] {id {:user/str1 v}}])))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form! (Input! :user/num1 num1 :type "number" :parse parse-long)
                    :commit (fn [{v :user/num1}]
                              [[`Num1FormSubmit id v] {id {:user/num1 v}}])))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form! (Checkbox! :user/bool1 bool1)
                    :commit (fn [{v :user/bool1}]
                              [[`Bool1FormSubmit id v] {id {:user/bool1 v}}]))))))))

(e/defn Str1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/str1 v}]]
      (e/Offload #(try (d/transact! !conn tx) ::cqrs/ok (catch Exception e (doto ::fail (prn 'Str1FormSubmit e))))))))

(e/defn Num1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/num1 v}]]
      (e/Offload #(try (d/transact! !conn tx) ::cqrs/ok (catch Exception e (doto ::fail (prn 'Num1FormSubmit e))))))))

(e/defn Bool1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/bool1 v}]]
      (e/Offload #(try (d/transact! !conn tx) ::cqrs/ok (catch Exception e (doto ::fail (prn 'Bool1FormSubmit e))))))))

(e/defn Forms3b-inline-submit []
  (binding [cqrs/*effects* {`Str1FormSubmit Str1FormSubmit
                            `Num1FormSubmit Num1FormSubmit
                            `Bool1FormSubmit Bool1FormSubmit}]
    (let [db (e/server (e/watch !conn))]
      (Service
        (e/with-cycle* first [edits (e/amb)]
          (UserForm db 42 edits))))))