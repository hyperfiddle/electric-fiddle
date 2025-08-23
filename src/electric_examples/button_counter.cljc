(ns electric-examples.button-counter
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Counter []
  (dom/button
    (let [n (dom/On "click" (partial (fn [!n e] (swap! !n inc)) (atom 0)) 0)]
      (dom/text n))))
