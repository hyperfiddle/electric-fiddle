(ns electric-tutorial.tutorial-7guis-1-counter
  (:require
   [hyperfiddle.electric3 :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn PeerAgnosticCounter [n Inc txt]
  (dom/p (dom/text n))
  (dom/button
    (dom/text txt)
    (let [spend! ($ e/Token ($ dom/On "click"))
          busy? (boolean spend!)]
      (dom/props {:disabled busy?, :aria-busy busy?})
      (when spend! (spend! ($ Inc))))))

(e/defn Counter []
  (let [!n  (atom 0)
        n   (e/watch !n)
        Inc (e/fn [] (swap! !n inc))]
    ($ PeerAgnosticCounter n Inc "count on client"))
  (let [!n  (e/server (atom 0))
        n   (e/server (e/watch !n))
        Inc (e/fn [] (e/server (swap! !n inc)))]
    ($ PeerAgnosticCounter n Inc "count on server")))
