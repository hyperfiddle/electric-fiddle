(ns electric-examples.discrete-events
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn ButtonClickCausal []
  (e/client
    (let [click-event (dom/button (dom/text "Click me")
                        (dom/On "click" identity nil))]
      (dom/pre (dom/text (if click-event
                           (+ (.-timeStamp click-event) (.-timeOrigin js/performance))
                           "Not clicked yet"))))))

(e/defn ButtonClickNotCausal []
  (e/client
    (let [click-event (dom/button (dom/text "Click me")
                        (dom/On "click" identity nil))]
      (dom/pre (dom/text (if click-event
                           (.getTime (new js/Date))
                           "Not clicked yet"))))))

(e/defn ButtonClick []
  (ButtonClickCausal)
  (ButtonClickNotCausal))