(ns electric-tutorial.counter
  (:require [clojure.math :refer [floor-div]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Countdown [from-n-inclusive dur-ms]
  (+ (inc from-n-inclusive) ; end on 1
    (floor-div
      (- (e/snapshot (e/SystemTimeMs)) (e/SystemTimeMs))
      (/ dur-ms from-n-inclusive))))

(e/defn Clicker [n F!]
  (e/client
    (println F!) ; serializable e/fn
    (dom/div
      (dom/text "count: " n " ")
      (dom/button (dom/text "inc")
        (e/for [[t e] (dom/OnAll "click")]
          (dom/text " " (F! t)))))))

#?(:clj (defonce !n (atom 0)))

(e/defn Counter []
  (e/server
    (let [n (e/watch !n)]
      (Clicker n (e/fn [t]
                   (t (e/server (case (e/Task (m/sleep 2000)) (swap! !n inc))))
                   (Countdown 10 2000)))))) ; progress indicator