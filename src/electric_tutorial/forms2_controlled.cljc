(ns electric-tutorial.forms2-controlled
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [Input Checkbox]]))

(def state0 {:user/str1 "hello" :user/num1 42 :user/bool1 true})

(e/defn UserForm [{:keys [user/str1 user/num1 user/bool1]}]
  (dom/fieldset
    (dom/dl
      {:user/str1
       (e/amb (dom/dt (dom/text "str1"))
         (dom/dd (Input str1)))

       :user/num1
       (e/amb (dom/dt (dom/text "num1"))
         (dom/dd (-> (Input num1 :type "number") parse-long)))

       :user/bool1
       (e/amb (dom/dt (dom/text "bool1"))
         (dom/dd (Checkbox bool1)))})))

(e/defn Forms2-controlled []
  (let [m (e/with-cycle [m state0] ; pure!
            (e/amb ; tricky cycle on e/amb
              (UserForm m)
              (UserForm m)))]
    (dom/code (dom/text (e/server (pr-str m))))))

; equivalent - dataflow recursion via atom
(e/defn Forms2-controlled' []
  (let [!m (atom state0) m (e/watch !m)]
    (reset! !m ; product
      (e/amb
        (UserForm m)
        (UserForm m)))
    (dom/code (dom/text (pr-str m)))))