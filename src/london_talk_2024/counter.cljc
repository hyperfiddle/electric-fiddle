(ns london-talk-2024.counter
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [missionary.core :as m]))

(e/defn Counter [label n F!]
  (e/client
    (dom/div
      (dom/text label ": " n " ")
      (dom/button (dom/text "inc")
        (let [e ($ dom/On "click")]
          (when-let [release! ($ e/Token e)]
            (dom/props {:disabled true, :aria-busy true})
            (dom/text " " ($ F! release!))))))))

(e/defn CounterMain []
  (let [!c (e/client (atom 0)), c (e/client (e/watch !c))
        !s (e/server (atom 0)), s (e/server (e/watch !s))]

    ($ Counter "client" c (e/fn [!] (! (e/client (swap! !c inc)))))
    ($ Counter "server" s (e/fn [!]
                            (! (e/server (Thread/sleep 300) (swap! !s inc))) ; illegal block
                            ($ e/SystemTimeMs)))))


(e/defn CounterDemo []
  ($ CounterMain))
