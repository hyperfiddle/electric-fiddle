(ns electric-tutorial.backpressure
  (:require [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Backpressure []
  (let [c (e/client (/ (e/SystemTimeMs) 1000))
        s (e/server (double (/ (e/SystemTimeMs) 1000)))]

    (println (int c)) ; check console
    (println (int s))

    (dom/div (dom/text "client time: " c))
    (dom/div (dom/text "server time: " s))
    (dom/div (dom/text "difference: " (.toPrecision (- s c) 2)))))