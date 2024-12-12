(ns electric-tutorial.counter
  (:require [clojure.math :refer [floor-div]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Countdown [from-n-inclusive dur-ms]
  (let [left (+ (inc from-n-inclusive)  ; end on 1
               (floor-div
                 (- (e/snapshot (e/System-time-ms)) (e/System-time-ms))
                 (/ dur-ms from-n-inclusive)))]
    (if (neg? left) "⌛" left)))

(e/defn Clicker [n F!]
  (e/client
    (println F!) ; serializable e/fn - see console
    (dom/div
      (dom/text "count: " n " ")
      (dom/button (dom/text "inc")
        (e/for [[t e] (dom/On-all "click")]
          (dom/text " " (F! t)))))))

#?(:clj (defonce !n (atom 0))) ; shared memory global state

(e/defn Counter []
  (let [n (e/server (e/watch !n))]
    (Clicker n (e/fn [t]
                 (case (e/server ({} (swap! !n (e/Task (m/sleep 2000 inc))) ::ok))
                   ::fail nil         ; todo
                   ::ok (e/client (t)))
                 (Countdown 10 2000))))) ; progress indicator
