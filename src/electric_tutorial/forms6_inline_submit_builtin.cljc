(ns electric-tutorial.forms6-inline-submit-builtin
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Field]]
            [hyperfiddle.input-zoo0 :refer [InputSubmit! CheckboxSubmit!]]
            [electric-tutorial.forms3-crud :refer [Service #?(:clj !conn)]]))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Field id :user/str1
                    (InputSubmit! str1))) ; bundled commit/discard

          (dom/dt (dom/text "num1"))
          (dom/dd (Field id :user/num1
                    (e/for [[t v] (InputSubmit! num1 :type "number")] ; bundled commit/discard
                      [t (parse-long v)])))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Field id :user/bool1
                    (CheckboxSubmit! bool1)))))))) ; bundled commit/discard

(e/defn Forms6-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true) ; NO stage at form level
                (UserForm db 42) #_(Stage :debug true))]
    (Service edits)))