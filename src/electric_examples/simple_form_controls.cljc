(ns electric-examples.simple-form-controls
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Button!* Checkbox* Input*]]))

(e/defn Increment! [!counter latency simulate-failure?]
  (e/server
    (e/Offload (fn []
                 (when (pos? latency) (Thread/sleep latency))
                 (if simulate-failure?
                   ::failed
                   (do (swap! !counter inc)
                       ::ok))))))

(e/defn SimpleFormControls []
  (let [!counter (e/server (atom 0))]
    (e/client
      (dom/div (dom/props {:style {:display :flex, :flex-direction :column, :align-items :start}})
        (dom/p (dom/text "Count: " (e/server (e/watch !counter))))
        (let [[token error] (Button!* :label "Add 1")
              fail?   (dom/div (Checkbox* true :label "Failure"))
              latency (dom/div (-> (Input* 500 :label "Latency (ms) " :type "number" :min 0) (parse-long) (or 0)))]
          fail? latency ; force lazy let bindings
          (dom/span (dom/text " " error))
          (when (some? token)
            (case (Increment! !counter latency fail?)
              ::ok (token)
              ::failed (token "Increment failed"))))))))