(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.chat :refer
             [Login Presence! Present Query-chats SendMessage ->msg-record css
              #?(:clj send-message!) #?(:clj !present)]]))

(e/defn Query-chats-optimistic [username edits]
  (prn 'edits (e/as-vec edits))
  ; Hack in optimistic updates; we'll formalize a better pattern in Todos tutorial
  (let [server-xs (e/server (Query-chats)) ; reuse server query (differential!)
        client-xs (e/for [[t msg] edits] ; cheap optimistic updates
                       (-> (->msg-record username msg t) ; local prediction
                         (assoc ::pending true)))]
    (prn 'pending (e/as-vec client-xs))
    ; todo reconcile records by id for count consistency
    (e/amb client-xs server-xs))) ; e/amb is differential concat here

(e/defn Channel [msgs]
  (prn 'msgs (e/as-vec msgs))
  (dom/ul (dom/props {:class "channel"})
    ; Our very naive optimistic update impl here means the query can return more
    ; than 10 elements, so we use a css fixed height with overflow-y:clip so we
    ; don't have to bother maintaining the length of the query.
    (e/for [{:keys [username msg ::pending]} msgs] ; render list bottom up in CSS
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
    (Channel (Query-chats-optimistic username edits))
    (SendMessage username)
    (Login username)))

(e/defn ChatMonitor []
  (dom/style (dom/text css))
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        edits (e/with-cycle* first [edits (e/amb)] ; loop in-flight edits
                (ChatApp username present edits))]

    (prn 'edits edits)
    (e/for [[t msg] (e/Filter some? edits)]
      (case (e/server
              (let [record (->msg-record username msg)] ; secure
                (e/Offload #(do (Thread/sleep 500) ; artificial latency
                              (send-message! record) ::ok))))
        ::ok (t)))))