(ns electric-tutorial.basictext-input
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn Input [v]
  (dom/input (dom/props {:value v})
    (dom/On "input" #(-> % .-target .-value)
      v)))

(e/defn BasicTextInput []
  (let [s (Input "hello")]
    (dom/p (dom/text s))))
