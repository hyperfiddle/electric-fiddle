(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defonce !msgs (atom (repeat 10 nil)))) ; list of {:db/id x :msg y}

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


#?(:clj (defn send-message! [!msgs msg] (swap! !msgs #(take 10 (cons msg %)))))
#?(:clj (defn delayed [n f] (Thread/sleep n) (f)))

(e/defn Query-todos [!msgs] (e/server (e/diff-by :db/id (reverse (e/watch !msgs))))) ; O(n) bad, fixme

(e/defn Chat []
  (e/server
    (dom/ul
      (e/for [{:keys [msg]} (Query-todos !msgs)]
        (dom/li
          (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")}})
          (dom/text msg))))

    (e/client
      (let [pending (InputSubmit :placeholder "Type a message" :maxlength 100)]
        (e/for [[v t] pending]
          (let [msg {:db/id (random-uuid) :msg v}]
            (case (e/server
                    (case (e/Offload #(delayed 500 (partial send-message! !msgs msg))) ::ok))
              ::ok (t))))

        (dom/props {:style {:background-color (when (pos? (e/Count pending)) "yellow")}})
        (dom/text (e/Count pending))))))