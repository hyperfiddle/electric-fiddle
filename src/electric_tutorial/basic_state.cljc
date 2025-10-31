(ns electric-tutorial.basic-state
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn Input [v]
  (dom/input (dom/props {:value v})
    (dom/On "input" #(-> % .-target .-value) v)))

(e/defn BasicState []
  (let [!s (atom "hello")
        s (e/watch !s)]
    (dom/p (dom/text s))
    (reset! !s (Input s))))
