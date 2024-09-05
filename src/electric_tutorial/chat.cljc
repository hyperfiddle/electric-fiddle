(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.forms :refer [InputSubmit]]))

#?(:clj (defn send-message! [!msgs msg] (swap! !msgs #(take 10 (cons msg %)))))
(e/defn Query-todos [!db] (e/server (e/diff-by :db/id (reverse (e/watch !db))))) ; O(n) bad, fixme

#?(:clj (defonce !db (atom (repeat 10 nil))))

(e/defn Chat []
  (dom/ul
    (e/for [{:keys [msg]} (e/server (Query-todos !db))]
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")}})
        (dom/text msg))))

  (e/client
    (let [pending (InputSubmit :placeholder "Type a message" :maxlength 100)]
      (e/for [[v t] pending]
        (case (e/server
                (let [msg {:db/id (random-uuid) :msg v}]
                  (case (e/Offload #(send-message! !db msg)) ::ok)))
          ::ok (t)))

      (dom/props {:style {:background-color (when (pos? (e/Count pending)) "yellow")}})
      (dom/text (e/Count pending)))))