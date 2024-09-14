(ns electric-tutorial.lifecycle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn BlinkerComponent []
  (dom/h1 (dom/text "blink!"))
  (println 'component-did-mount)
  (e/on-unmount #(println 'component-will-unmount)))

(e/defn Lifecycle []
  (e/client
    (when (zero? (mod (e/SystemTimeSecs) 2))
      (BlinkerComponent))))
