(ns electric-tutorial.temperature-converter
  (:require
    [clojure.math :refer [round]]
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-forms5 :refer [Input]]
    [hyperfiddle.electric-dom3 :as dom]))

(defn c->f [c] (+ (* c (/ 9 5)) 32))
(defn f->c [f] (* (- f 32) (/ 5 9)))

(e/defn TemperatureConverter []
  (let [!v (atom 0), v (e/watch !v)]
    (dom/dl

      (dom/dt (dom/text "Celsius"))
      (dom/dd (some->> (Input v :type "number")
                not-empty parse-long (reset! !v)))

      (dom/dt (dom/text "Fahrenheit"))
      (dom/dd (some->> (Input (round (c->f v)) :type "number")
                not-empty parse-long f->c round (reset! !v))))))
