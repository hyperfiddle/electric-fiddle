(ns electric-tutorial.button-counter
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]))

(e/defn ButtonCounter []
  (e/client
    (dom/button
      (let [!n (atom 0)
            f! (fn [e] (swap! !n inc))
            n (dom/On "click" f! 0)]
        (dom/text n)))))
