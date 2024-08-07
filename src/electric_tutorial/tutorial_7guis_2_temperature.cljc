(ns electric-tutorial.tutorial-7guis-2-temperature
  (:require [clojure.math]
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(defn c->f [c] (+ (* c (/ 9 5)) 32))
(defn f->c [f] (* (- f 32) (/ 5 9)))

;; random set through missionary ; TODO do we want to keep this?
(defn set-random-valueT [!t]
  (m/sp (loop []
          (m/? (m/sleep 1000))
          (reset! !t (rand-int 40))
          (recur))))

;; random set through Electric ; TODO do we want to keep this?
(e/defn SetRandomValue [!t]
  (when-some [spend! ($ e/CyclicToken true)] ; TODO document CyclicToken
    (spend! (reset! !t ($ e/Task (m/sleep 1000 (rand-int 40)))))))

(e/defn TemperatureConverter []
  (let [!t (atom 0), t (e/watch !t)]
    ($ e/Task (set-random-valueT !t))   ; concurrent writer
    (dom/dl
      (dom/dt (dom/text "Celsius"))
      (dom/dd (dom/input
                (dom/props {:type "number"})
                (when-not ($ dom/Focused?)
                  (set! (.-value dom/node) (clojure.math/round t)))
                ($ dom/On "input" (fn [e]
                                    (some->> (-> e .-target .-value parse-long) (reset! !t))))))
      (dom/dt (dom/text "Fahrenheit"))
      (dom/dd (dom/input
                (dom/props {:type "number"})
                (when-not ($ dom/Focused?)
                  (set! (.-value dom/node) (clojure.math/round (c->f t))))
                ($ dom/On "input" (fn [e]
                                    (some->> (-> e .-target .-value parse-long) f->c (reset! !t)))))))))

