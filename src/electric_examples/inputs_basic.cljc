(ns electric-examples.inputs-basic
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn BasicInput []
  (e/client
    (let [s (dom/input ; dom elements return their final child value
              (dom/props {:type "text", :class "foo"})
              (dom/On "input" (fn [event] (-> event .-target .-value)) ""))]
      (dom/pre (dom/text (pr-str s))))))