(ns electric-tutorial.lifecycle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn BlinkerComponent []
  (e/Tap-diffs #(prn 'mount %) ::x) ; see console
  (dom/h1 (dom/text "blink!"))
  (println 'mount)
  (e/On-unmount #(println 'unmount)))

(e/defn Lifecycle []
  (e/client
    (e/Tap-diffs #(prn 'when %) ; see console
      (when (zero? (mod (e/System-time-secs) 2))
        (BlinkerComponent)))))
