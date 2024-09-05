(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (def !msgs (atom (repeat 10 nil))))

#?(:cljs (defn read! [maxlength node]
           (when-some [v (not-empty (subs (.-value node) 0 maxlength))]
             (set! (.-value node) "") v)))

#?(:cljs (defn submit! [maxlength e]
           (when (= "Enter" (.-key e))
             (read! maxlength (.-target e)))))

(e/defn InputSubmit [& {:keys [maxlength] :or {maxlength 100} :as props}] ; destr can cause roundtrips, fixme
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (dom/OnAll "keydown" (partial submit! maxlength)))))

#?(:clj (defn slow-send-message! [!msgs msg]
          (Thread/sleep 500)
          (swap! !msgs #(take 10 (cons msg %)))))

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
      (let [pending (InputSubmit :placeholder "Type a message" :maxlength 100)]
        (e/for [[v t] pending]
          (case (e/server
                  (case (e/Offload #(slow-send-message! !msgs v)) ::ok))
            ::ok (t)))

        (dom/props {:style {:background-color (when (pos? (e/Count pending)) "yellow")}})
        (dom/text (e/Count pending))))))