(ns electric-tutorial.forms3c-inline-submit-builtin
  #?(:cljs (:require-macros electric-tutorial.forms3c-inline-submit-builtin))
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Form Field Service]]
            [hyperfiddle.input-zoo0 :as z :refer [Checkbox! Input!]]
            [electric-tutorial.forms3a-form :refer
             [#?(:clj !conn) #?(:clj expand-tx-effects)]]))

(e/defn UserForm [db id]
  (dom/fieldset (dom/legend (dom/text "enter/esc mapped to commit/discard"))
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form
                    (Field id :user/str1
                      (Input! str1))
                    :show-buttons false))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form ; field stage
                    (Field id :user/num1
                      (e/for [[t v] (Input! num1 :type "number")]
                        [t (parse-long v)]))
                    :show-buttons false))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form ; field stage
                    (Field id :user/bool1
                      (Checkbox! bool1))
                    :show-buttons false))))))) ; bundled commit/discard

(e/defn Forms3c-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))
        txns (UserForm db 42)]
    (Service (e/server (identity expand-tx-effects)) txns)))