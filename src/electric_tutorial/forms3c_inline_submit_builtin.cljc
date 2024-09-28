(ns electric-tutorial.forms3c-inline-submit-builtin
  #?(:cljs (:require-macros electric-tutorial.forms3c-inline-submit-builtin))
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :refer [Form Service]]
            [hyperfiddle.input-zoo0 :as z :refer [Checkbox! Input!]]
            [electric-tutorial.forms3a-form :refer [#?(:clj !conn)]]
            [electric-tutorial.forms3b-inline-submit :refer
             [Str1FormSubmit Num1FormSubmit Bool1FormSubmit]]))

(e/defn UserForm [db id]
  (dom/fieldset (dom/legend (dom/text "enter/esc mapped to commit/discard"))
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form (Input! str1 :parse #(hash-map 0 %))
                    :commit (fn [dirtys]
                              (let [{v 0} (apply merge dirtys)]
                                [[`Str1FormSubmit id v] {}]))
                    :show-buttons false))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form (Input! num1 :type "number" :parse #(hash-map 0 (parse-long %)))
                    :commit (fn [dirtys]
                              (let [{v 0} (apply merge dirtys)]
                                [[`Num1FormSubmit id v] {}]))
                    :show-buttons false))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form (Checkbox! bool1 :parse #(hash-map 0 %))
                    :commit (fn [dirtys]
                              (let [{v 0} (apply merge dirtys)]
                                [[`Bool1FormSubmit id v] {}]))
                    :show-buttons false)))))))

(e/defn Forms3c-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))]
    (Service {`Str1FormSubmit Str1FormSubmit
              `Num1FormSubmit Num1FormSubmit
              `Bool1FormSubmit Bool1FormSubmit}
      (UserForm db 42))))