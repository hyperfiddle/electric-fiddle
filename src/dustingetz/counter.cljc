(ns dustingetz.counter
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [missionary.core :as m]))

(e/defn Counter [label n F!]
  (e/client
    (dom/div
      (dom/text label ": " n " ")
      (dom/button (dom/text "inc")
        #_(let [e ($ dom/On "click")]
          (when-let [t ($ e/Token e)]
            (dom/props {:disabled true, :aria-busy true})
            (dom/text " " ($ F! t))))

        (e/for [[e t] ($ dom/OnAll "click")]
          (dom/text " " ($ F! t)))))))

(e/defn CounterMain []
  (let [!c (e/client (atom 0)), c (e/client (e/watch !c))
        !s (e/server (atom 0)), s (e/server (e/watch !s))]

    ($ Counter "client" c (e/fn [!] (! (e/client (swap! !c inc)))))
    ($ Counter "server" s (e/fn [!]
                            (! (e/server ($ e/Task (m/via m/blk (Thread/sleep 500) (swap! !s inc)))))
                            #_(! (e/server (Thread/sleep 500) (swap! !s inc))) ; illegal block
                            ($ e/SystemTimeMs)))))


(e/defn CounterDemo []
  ($ CounterMain))
