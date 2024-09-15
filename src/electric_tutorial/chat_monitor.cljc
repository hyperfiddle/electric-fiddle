(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.chat :refer
             [Login Presence! Present SendMessage Query-chats msg-ctor
              #?(:clj !db) #?(:clj send-message!) #?(:clj !present)]]))

(e/defn Channel [msgs]
  (dom/ul (dom/props {:class "channel"})
    (e/for [{:keys [username msg ::pending]} msgs]
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")
                                  :grid-template-columns "1fr 1fr"}})
        (dom/props {:aria-busy pending})
        (dom/span (dom/strong (dom/text username)) (dom/text " " msg))
        (dom/span (dom/props {:style {:text-align "right"}})
          (dom/text (if pending "pending" "ok")))))))

(e/defn ChatApp [username present edits]
  (e/amb
    (Present present)
    (dom/hr (e/amb)) ; silence nil
    (let [pending-msgs (e/for [[_ v] edits]
                         (-> (msg-ctor username v) ; optimistic
                           (assoc ::pending true)))
          server-msgs (e/server (Query-chats !db))]
      (Channel (e/amb pending-msgs server-msgs))) ; todo reconcile records
    (SendMessage username)
    (Login username)))

(e/defn ChatMonitor []
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        edits (e/with-cycle* first [edits (e/amb)]
                (ChatApp username present edits))]

    (prn 'edits edits)
    (e/for [[t v] (e/Filter some? edits)]
      (case (e/server
              (let [msg (msg-ctor username v)] ; secure
                (case (e/Offload #(do (Thread/sleep 500) ; artificial latency
                                    (send-message! !db msg))) ::ok)))
        ::ok (t)))))