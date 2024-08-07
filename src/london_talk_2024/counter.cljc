(ns london-talk-2024.counter
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [missionary.core :as m]))

(e/defn Counter [label n Inc]
  (e/client
    (dom/div
      (dom/text label ": " n " ")
      (dom/button (dom/text "inc")

        (let [e ($ dom/On "click")]
          (when-let [clear! ($ e/Token e)]
            (dom/props {:disabled true, :aria-busy true})
            (clear! ($ Inc))))

        #_(e/cursor [[e clear!] ($ dom/OnAll "click")]
          (clear! ($ Inc)))))))

(defn slow-swap! [!x f]
  (m/ap (m/? (m/sleep 500)) (swap! !x f)))

(e/defn CounterMain []
  (let [!c (e/client (atom 0)) c (e/client (e/watch !c))
        !s (e/server (atom 0)) s (e/server (e/watch !s))]

    ($ Counter "client" c (e/fn [] (e/client (swap! !c inc))))
    ($ Counter "server" s (e/fn [] (e/server (swap! !s inc)
                                     #_({} (Thread/sleep 500) (swap! !s inc))
                                     #_(case ($ e/Task (m/sleep 500)) (swap! !s inc))
                                     #_(e/input (slow-swap! !s inc)))))))

(e/defn CounterDemo []
  (e/client ($ CounterMain))
  (e/server ($ CounterMain)))
