(ns electric-tutorial.temperature
  (:require [clojure.math :refer [round]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.input-zoo :refer [Input]]
            [missionary.core :as m]))

(defn c->f [c] (+ (* c (/ 9 5)) 32))
(defn f->c [f] (* (- f 32) (/ 5 9)))

(defn random-writer [!t]
  (m/sp (loop [] (m/? (m/sleep 1000)) (reset! !t (rand-int 40)) (recur))))

; Since the state is local (no latency, no transaction) we drop to the lower
; signal API with no token.

(e/defn TemperatureConverter []
  (let [!v (atom 0), v (e/watch !v)]     ; recursive binding via atom
    (e/Task (random-writer !v))          ; concurrent writer
    (dom/dl
      (dom/dt (dom/text "Celsius"))
      (dom/dd (some->> (Input (round v) :type "number")
                not-empty parse-long (reset! !v))) ; loop v
      (dom/dt (dom/text "Fahrenheit"))
      (dom/dd (some->> (Input (round (c->f v)) :type "number")
                not-empty parse-long f->c (reset! !v)))))) ; loop v