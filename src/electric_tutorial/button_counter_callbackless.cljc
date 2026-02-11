(ns electric-tutorial.button-counter-callbackless
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn Clicker [n]
  (dom/button (dom/text "click!")
    (dom/On "click" (fn [e] (inc n)) n)))

(e/defn ButtonCounterCallbackless []
  (e/client
    (let [!n (atom 0), n (e/watch !n)]
      (reset! !n (Clicker n))
      (dom/text n))))
