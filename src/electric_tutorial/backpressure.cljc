(ns electric-tutorial.backpressure
  (:require [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Backpressure []
  (let [c (e/client ($ e/SystemTimeSecs))
        s (e/server (double ($ e/SystemTimeSecs)))]

    (println "s" (int s))
    (println "c" (int c))

    (dom/div (dom/text "client time: " c))
    (dom/div (dom/text "server time: " s))
    (dom/div (dom/text "difference: " (.toPrecision (- s c) 2)))))
