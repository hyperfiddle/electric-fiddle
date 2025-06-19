(ns electric-examples.inputs-basic
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn UncontrolledInput []
  (dom/input (dom/props {:type "text"})
    (dom/On "input" (fn [e] (-> e .-target .-value)) "")))

(e/defn UncontrolledInputDemo []
  (e/client
    (let [s (UncontrolledInput)]
      (dom/pre (dom/text (pr-str s))))))