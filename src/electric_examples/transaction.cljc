(ns electric-examples.transaction
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Increment! [!counter]
  (e/server
    (e/Offload (fn [] (Thread/sleep 500) ; artificial latency
                 (if (odd? (rand-int 2)) ; artificial failure
                   ::failed
                   (do (swap! !counter inc) ; actual effect
                       ::ok))))))

(e/defn TransactionalButton [label]
  (e/client
    (dom/button (dom/text label)
      (let [click-event (dom/On "click" identity nil)
            [token error] (e/Token click-event)
            pending? (and (some? click-event) (some? token))
            failed? (some? error)
            succeeded? (and (some? click-event) (nil? token) (nil? error))]
        (dom/props {:disabled pending?
                    :style {:background-color (cond pending?   "yellow"
                                                    failed?    "pink"
                                                    succeeded? "lime")}})
        [token error]))))

(e/defn Transaction []
  (let [!counter (e/server (atom 0))]
    (e/client
      (dom/p (dom/text "Count: " (e/server (e/watch !counter))))
      (let [[token error] (TransactionalButton "Add 1")]
        (dom/span (dom/text " " error))
        (when (some? token)
          (case (Increment! !counter)
            ::ok (token)
            ::failed (token "Increment failed")))))))