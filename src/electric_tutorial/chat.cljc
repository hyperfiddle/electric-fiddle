(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

#?(:clj (def !msgs (atom (repeat 10 nil))))

(e/defn Chat []
  (e/server
    (dom/ul
      (e/for [msg (e/diff-by ; O(n) bad, fixme
                    identity ; fixme flicker on duplicate
                    (reverse (e/watch !msgs)))]
        (dom/li
          (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")}})
          (dom/text msg))))

    (e/client
      (let [pending (dom/input (dom/props {:placeholder "Type a message" :maxlength 100})
                      (dom/OnAll "keydown" #(when (= "Enter" (.-key %))
                                              (when-some [v! (not-empty (-> % .-target .-value))] ; untrusted
                                                (set! (.-value dom/node) "")
                                                (.slice v! 0 100)))))]
        (e/for [[v t] pending]
          (t (e/server v ; preload v (hinting the inevitable outcome of the task)
               (case (e/Task (m/sleep 300)) ; todo fix m/sp and use here
                 ({} (swap! !msgs #(take 10 (cons v %))) nil)))))

        (dom/props {:style {:background-color (when (pos? (e/Count pending)) "yellow")}})
        (dom/text (e/Count pending))))))