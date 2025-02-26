(ns electric-tutorial.temperature2
  (:require [clojure.math :refer [round]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Input]]
            [missionary.core :as m]))

(defn c->f [c] (+ (* c (/ 9 5)) 32))
(defn f->c [f] (* (- f 32) (/ 5 9)))

(def random-writer (m/ap (m/amb 0 (loop [] (m/? (m/sleep 1000))
                                     (m/amb (rand-int 40) (recur))))))

(e/defn Temperature2 []
  (let [!v (atom 0) v (e/watch !v)
        v' (e/amb
             (e/input random-writer) ; concurrent writer
             (dom/dl
               (e/amb
                 (dom/dt (dom/text "Celsius"))
                 (dom/dd (some-> (Input v :type "number")
                           not-empty parse-long))
                 (dom/dt (dom/text "Fahrenheit"))
                 (dom/dd (some-> (Input (round (c->f v)) :type "number")
                           not-empty parse-long f->c round)))))]
    (reset! !v v')))