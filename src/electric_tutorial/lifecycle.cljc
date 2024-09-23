(ns electric-tutorial.lifecycle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn BlinkerComponent []
  (e/Tap-diffs #(prn 'mount %) ::x) ; see console
  (dom/h1 (dom/text "blink!"))
  (println 'mount)
  (e/on-unmount #(println 'unmount)))

(e/defn Lifecycle []
  (e/client
    (e/Tap-diffs #(prn 'when %) ; see console
      (when (zero? (mod (e/SystemTimeSecs) 2))
        (BlinkerComponent)))))
