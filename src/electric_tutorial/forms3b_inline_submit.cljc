(ns electric-tutorial.forms3b-inline-submit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form! Service try-ok]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox! Checkbox]]
            [electric-tutorial.forms3a-form :refer
             [Query-record #?(:clj !conn) #?(:clj transact-unreliable)]]))

(e/defn UserForm [db id edits]
  (dom/fieldset (dom/legend (dom/text "transactional fields with inline submit, esc/enter"))
    (let [debug (Checkbox true :label "debug")
          show-buttons (Checkbox false :label "show-buttons")
          auto-submit (Checkbox false :label "auto-submit")
          {:keys [user/str1 user/num1 user/bool1]} (Query-record db id edits)]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form! (Input! :user/str1 str1)
                    :commit (fn [{v :user/str1}]
                              [[`Str1FormSubmit id v] {id {:user/str1 v}}])
                    :auto-submit auto-submit
                    :show-buttons show-buttons
                    :debug debug))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form! (Input! :user/num1 num1 :type "number" :parse parse-long)
                    :commit (fn [{v :user/num1}]
                              [[`Num1FormSubmit id v] {id {:user/num1 v}}])
                    :auto-submit auto-submit
                    :show-buttons show-buttons
                    :debug debug))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form! (Checkbox! :user/bool1 bool1)
                    :commit (fn [{v :user/bool1}]
                              [[`Bool1FormSubmit id v] {id {:user/bool1 v}}])
                    :auto-submit auto-submit
                    :show-buttons show-buttons
                    :debug debug)))))))

(e/defn Str1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/str1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx))))))

(e/defn Num1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/num1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx))))))

(e/defn Bool1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/bool1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx))))))

(e/defn Forms3b-inline-submit []
  (binding [cqrs/*effects* {`Str1FormSubmit Str1FormSubmit
                            `Num1FormSubmit Num1FormSubmit
                            `Bool1FormSubmit Bool1FormSubmit}]
    (let [db (e/server (e/watch !conn))]
      (Service
        (e/with-cycle* first [edits (e/amb)]
          (e/amb
            (UserForm db 42 edits)
            (UserForm db 42 edits)))))))