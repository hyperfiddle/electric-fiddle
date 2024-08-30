(ns electric-tutorial.lifecycle
  (:require
   [hyperfiddle.electric3 :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn BlinkerComponent []
  (e/client
    (dom/h1 (dom/text "blink!"))
    (println 'component-did-mount)
    (e/on-unmount #(println 'component-will-unmount))))

(e/defn Lifecycle []
  (e/client
    (when (zero? (mod ($ e/SystemTimeSecs) 2))
      ($ BlinkerComponent))))
