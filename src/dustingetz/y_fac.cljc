(ns dustingetz.y-fac
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Y [Gen]
  (e/call
    (e/fn   [F] (F F)) ; call-with-self
    (e/fn F [F] (Gen (e/fn Recur [x]
                       (e/call (F F) x))))))

(e/defn Trace [x]
  (e/client (dom/div (dom/text x)))
  x)

(e/defn Fac [Recur]
  (e/fn [x]
    (Trace
      (case x
        0 1
        (* x (Recur (dec x)))))))

(e/defn Y-Fac []
  (e/call (Y Fac) 15))