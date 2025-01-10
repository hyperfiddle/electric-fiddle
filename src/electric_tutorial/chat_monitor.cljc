(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Form! Input!]]))

(e/defn Login [username]
  (dom/div
    (if (some? username)
      (dom/text "Authenticated as: " username)
      (dom/a (dom/props {:href "/auth"})
        (dom/text "Set login cookie (blank password)")))))

#?(:clj (defonce !present (atom {}))) ; session-id -> user

(e/defn Presence! [!present username]
  (e/server
    (let [session-id (get-in e/http-request [:headers "sec-websocket-key"])]
      (swap! !present assoc session-id (or username "anon"))
      (e/on-unmount #(swap! !present dissoc session-id)))
    (e/diff-by key (e/watch !present))))

(e/defn Present [present]
  (dom/div (dom/text "Present: " (e/Count present)))
  (dom/ul
    (e/for [[_ username] present]
      (dom/li (dom/text username)))))

#?(:clj (defonce !db (atom (repeat 10 nil)))) ; multiplayer
(e/defn Query-chats [] (e/server (e/diff-by :db/id (e/watch !db))))
#?(:clj (defn send-message! [msg] (swap! !db #(take 10 (cons msg %)))))
(defn ->msg-record [username msg & [tempid]]
  {:db/id (or tempid (random-uuid)) :username username :msg msg})


(e/defn Query-chats-optimistic [username edits]
  (prn 'edits (e/as-vec edits))
  ; Hack in optimistic updates; we'll formalize a better pattern in Todos tutorial
  (let [server-xs (e/server (Query-chats)) ; reuse server query (differential!)
        client-xs (e/for [[t cmd local-index] edits] ; cheap optimistic updates
                    (let [m (get local-index t)] ; token as tempid convention
                      (assoc m ::pending true)))]
    ; todo reconcile records by id for count consistency and fix flicker
    (e/amb client-xs server-xs))) ; e/amb is differential concat here

(e/defn Channel [msgs]
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

(e/defn SendMessageInput [username]
  (Form! (Input! ::msg "" :disabled (nil? username)
           :placeholder (if username "Send message" "Login to chat"))
    :show-buttons false ; enter to submit
    :genesis true ; immediately consume form, ready for next submit
    :commit (fn [{msg ::msg} tempid] (prn 'TodoCreate-commit msg)
              [[`Create-todo username msg]
               {tempid (->msg-record username msg tempid)}])))

(e/defn ChatApp [username present edits]
  (e/amb
    (Present present)
    (dom/hr (e/amb)) ; silence nil
    (Channel (Query-chats-optimistic username edits))
    (SendMessageInput username)
    (Login username)))

(e/defn Create-todo [username msg]
  (e/server ; secure, validate command here
    (let [record (->msg-record username msg)] ; secure
      (e/Offload #(try (Thread/sleep 500) ; artificial latency
                    (send-message! record) ::ok
                    (catch Exception e (doto ::rejected (prn e))))))))

(declare css)
(e/defn ChatMonitor []
  (dom/style (dom/text css))
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        edits (e/with-cycle* first [edits (e/amb)] ; loop in-flight edits
                (ChatApp username present edits))]
    (prn 'edits (e/as-vec edits))
    (e/for [[t [cmd & args] guess] (e/Filter some? edits)]
      (let [res (e/server
                  (case cmd
                    `Create-todo (e/apply Create-todo args)
                    (prn ::unmatched-cmd)))]
        (case res
          ::ok (t)
          ::fail nil))))) ; for retry, we're not done yet - todo finish demo

; .user-examples-target.Chat [aria-busy=true] { background-color: yellow; }
(def css "
[aria-busy=true] {background-color: yellow;}
.user-examples-target.ChatMonitor ul.channel li,
.user-examples-target.Chat ul.channel li { display: grid; }
.user-examples-target.Chat ul.channel,
.user-examples-target.ChatMonitor ul.channel {
    display: flex; flex-direction: column-reverse; /* bottom up */
    height: 220px; overflow-y: clip; /* pending pushes up server records */
    padding: 0; /* remove room for bullet in grid layout */ }")