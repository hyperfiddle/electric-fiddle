(ns electric-tutorial.lifecycle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn BlinkerComponent []
  (dom/h1 (dom/text "blink!")))

(e/defn Lifecycle []
  (e/client
    (let [is-even (zero? (mod (e/System-time-secs) 2))]
      (when is-even
        (BlinkerComponent)))))