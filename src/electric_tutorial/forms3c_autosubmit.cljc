(ns electric-tutorial.forms3c-autosubmit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Service]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3a-form :refer
             [#?(:clj !conn) #?(:clj expand-tx-effects)]]))

(e/defn UserForm [db id]
  (dom/fieldset (dom/legend (dom/text "transactional fields with autosubmit"))
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb ; 3 fields submitting edits concurrently
          (dom/dt (dom/text "str1"))
          (dom/dd (Field id :user/str1
                    (Input! str1)))

          (dom/dt (dom/text "num1"))
          (dom/dd (Field id :user/num1
                    (e/for [[t v] (Input! num1 :type "number")]
                      [t (parse-long v)])))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Field id :user/bool1
                    (Checkbox! bool1))))))))

(e/defn Forms3c-autosubmit []
  (let [db (e/server (e/watch !conn))
        txns (UserForm db 42)]
    (Service (e/server (identity expand-tx-effects)) txns)))