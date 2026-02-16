(ns electric-tutorial.backpressure
  (:require [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Backpressure []
  (let [c (e/client (/ (e/System-time-ms) 1000))
        s (e/server (double (/ (e/System-time-ms) 1000)))]

    (prn 'c (int c)) ; check console
    (prn 's (int s))

    (dom/div (dom/text "client time: " c))
    (dom/div (dom/text "server time: " s))
    (dom/div (dom/text "skew: " (.toPrecision (- s c) 2)))))