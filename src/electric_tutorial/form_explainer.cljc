(ns electric-tutorial.form-explainer
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer
             [Input! Checkbox! Checkbox* Form!]]))

(def state0 {:user/str1 "hello" :user/num1 42 :user/bool1 true})

(e/defn UserFormServer1 [{:keys [user/str1 user/num1 user/bool1]}]
  (Form!
    (e/amb ; concurrent individual edits!
      (Input! :user/str1 str1) ; fields are named
      (Input! :user/num1 num1 :type "number" :parse parse-long)
      (Checkbox! :user/bool1 bool1))
    :commit (fn [dirty-form] [dirty-form nil])))

(declare css)
(e/defn FormExplainer []
  (dom/style (dom/text css))
  (let [fail (dom/div (Checkbox* true :label "failure"))
        !x (e/server (atom state0)) x (e/server (e/watch !x))
        edits (e/amb
                (dom/div (UserFormServer1 x))
                (dom/div (UserFormServer1 x)))]
    fail
    (e/for [[t dirty-form] edits] ; concurrent edit processing
      (let [res (e/server
                  (if fail ::rejected (do (swap! !x merge dirty-form) ::ok)))]
        (case res
          ::ok (t)
          (t res))))
    (dom/pre (dom/text (pr-str x)))))

(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}")