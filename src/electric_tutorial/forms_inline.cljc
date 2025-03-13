(ns electric-tutorial.forms-inline ; used in form_explainer
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :as forms :refer
             [Input! Checkbox! Checkbox* Form! Service try-ok effects*]]
            [dustingetz.trivial-datascript-form :refer
             [#?(:clj ensure-conn!) #?(:clj transact-unreliable)]]))

(e/declare debug*)
(e/declare slow*)
(e/declare fail*)
(e/declare show-buttons*)
(e/declare auto-submit*)
(e/declare !conn)

(e/defn Str1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/str1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn Num1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/num1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn Bool1FormSubmit [id v]
  (e/server
    (let [tx [{:db/id id :user/bool1 v}]]
      (e/Offload #(try-ok (transact-unreliable !conn tx :fail fail* :slow slow*))))))

(e/defn UserForm [db id]
  (dom/fieldset (dom/legend (dom/text "UserForm"))
    (let [initial-form-fields
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))] ; query
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form! initial-form-fields
                      (e/fn Fields [{:keys [user/str1] :as form-fields}]
                        (Input! :user/str1 str1 :required true))
                    :Parse (e/fn [{:keys [user/str1] :as dirty-form-fields} unique-id]
                             [`Str1FormSubmit id str1]) ; command
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form! initial-form-fields
                      (e/fn Fields [{:keys [user/num1] :as form-fields}]
                        (Input! :user/num1 num1 :type "number" :Parse (e/fn [str] (parse-long str))))
                    :Parse (e/fn [{:keys [user/num1] :as dirty-form-fields} unique-id]
                             [`Num1FormSubmit id num1]) ; command
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form! initial-form-fields
                      (e/fn Fields [{:keys [user/bool1] :as form-fields}]
                        (Checkbox! :user/bool1 bool1))
                    :Parse (e/fn [{:keys [user/bool1] :as dirty-form-fields} unique-id]
                             [`Bool1FormSubmit id bool1]) ; command
                    :auto-submit auto-submit*
                    :show-buttons show-buttons*
                    :debug debug*)))))))

(declare css)

(e/defn Forms-inline []
  (dom/style (dom/text css))
  (binding [effects* {`Str1FormSubmit Str1FormSubmit
                      `Num1FormSubmit Num1FormSubmit
                      `Bool1FormSubmit Bool1FormSubmit}
            debug* (Checkbox* false :label "debug")
            slow* (Checkbox* true :label "latency")
            fail* (Checkbox* true :label "failure")
            show-buttons* (Checkbox* true :label "show-buttons")
            auto-submit* (Checkbox* false :label "auto-submit")]
    debug* fail* slow* auto-submit* show-buttons*
    (binding [!conn (e/server (ensure-conn!))]
      (let [db (e/server (e/watch !conn))]
        (Service
          (UserForm db 42))))))


(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}
dt:has(+ dd input:required)::after {content: \"*\"; color: crimson; font-weight: 400;}
dd:has(:invalid:required)::after {content: \"required\"; color: red; font-size: 0.85rem; padding: 0 0.75rem;}
")