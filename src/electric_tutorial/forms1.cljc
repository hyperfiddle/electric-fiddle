(ns electric-tutorial.forms1
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [Input* Checkbox*]]))

(e/defn UserForm [] ; no initial state
  (dom/fieldset
    (dom/dl
      {:user/str1
       (e/amb (dom/dt (dom/text "str1"))
         (dom/dd (Input*)))

       :user/num1
       (e/amb (dom/dt (dom/text "num1"))
         (dom/dd (-> (Input* :type "number") parse-long)))

       :user/bool1
       (e/amb (dom/dt (dom/text "bool1"))
         (dom/dd (Checkbox*)))})))

(e/defn Forms1 []
  (let [form (UserForm)]
    (dom/code (dom/text (pr-str form)))))