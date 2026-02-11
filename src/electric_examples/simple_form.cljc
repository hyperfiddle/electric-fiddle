(ns electric-examples.simple-form
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Form! Checkbox! Input!
                                                 SubmitButton! DiscardButton!]]))

(e/defn Increment! [!counter quantity latency simulate-failure?]
  (e/server
    (e/Offload (fn [] (try (when (pos? latency) (Thread/sleep latency))
                           (if simulate-failure?
                             (throw (ex-info "Increment failed" {}))
                             (do (swap! !counter + quantity)
                                 [::ok]))
                           (catch Throwable err [::failed (ex-message err)]))))))

(e/defn Field [name value Control! & {::keys [label] :as props}]
  (let [input-id (random-uuid)]
    (dom/dt (dom/label (dom/props {:for input-id}) (dom/text label)))
    (dom/dd (Control! name value (-> (assoc props :id input-id) (dissoc ::label))))))

(e/defn IncrementForm [form-state]
  (Form! form-state
      (e/fn [{::keys [quantity latency failure]}]
        (e/amb
          (dom/dl
            (e/amb
              (Field ::quantity quantity Input! ::label "Quantity" :required true :type "number"
                :Parse (e/fn [str]
                         (let [parsed (parse-long str)]
                           (if (zero? parsed)
                             (ex-info "Increment cannot be zero" {})
                             parsed))))
              (Field ::latency latency Input! ::label "Latency (ms)" :type "number" :min 0
                :Parse (e/fn [str] (or (parse-long str) 0)))
              (Field ::failure failure Checkbox! ::label "Failure")))
          (SubmitButton! :label "Submit")
          (DiscardButton! :label "Reset")))))

(e/defn SimpleForm []
  (e/client
    (let [!counter (e/server (atom 0))
          !form-state (atom {::quantity 1 ::latency 500 ::failure false})]
      (dom/p (dom/text "Count: " (e/server (e/watch !counter))))
      (let [[token form-fields] (IncrementForm (e/watch !form-state))]
        (when (some? token)
          (reset! !form-state form-fields)
          (let [{::keys [quantity latency failure]} form-fields
                [result reason] (Increment! !counter quantity latency failure)]
            (case result
              ::ok (token)
              ::failed (token reason))))))))