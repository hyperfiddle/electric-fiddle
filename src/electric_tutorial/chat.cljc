(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [InputSubmitCreate!]]))

(e/defn Login [username]
  (dom/div
    (if (some? username)
      (dom/text "Authenticated as: " username)
      (dom/a (dom/props {:href "/auth"})
        (dom/text "Set login cookie (blank password)")))))

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

(e/defn Channel [msgs]
  (dom/ul (dom/props {:class "channel"})
    (e/for [{:keys [username msg]} msgs] ; render list bottom up in CSS
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")
                                  :grid-template-columns "max-content auto" :gap "1ch"}})
        (dom/span (dom/strong (dom/text username)) (dom/text " " msg))))))

(e/defn SendMessage [username]
  (let [edits (InputSubmitCreate! :placeholder (if username "Message" "Login to chat")
                  :maxlength 100 :disabled (nil? username))]
    (dom/text " " (e/Count edits))
    edits))

#?(:clj (defonce !db (atom (repeat 10 nil)))) ; multiplayer
(e/defn Query-chats [] (e/server (e/diff-by :db/id (e/watch !db))))
#?(:clj (defn send-message! [msg] (swap! !db #(take 10 (cons msg %)))))
#?(:clj (defonce !present (atom {}))) ; session-id -> user

(defn ->msg-record [username msg & [tempid]]
  {:db/id (or tempid (random-uuid)) :username username :msg msg})

(declare css)
(e/defn Chat []
  (dom/style (dom/text css))
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        msgs (e/server (Query-chats))]
    (Present present)
    (dom/hr)
    (Channel msgs)
    (let [edits (SendMessage username)]
      (dom/props {:style {:background-color (when (pos? (e/Count edits)) "yellow")}})
      (e/for [[t msg] edits]
        (case (e/server
                (let [record (->msg-record username msg)] ; secure
                  (e/Offload #(do (send-message! record) ::ok))))
          ::ok (t))))
    (Login username)))

(def css "
.user-examples-target.ChatMonitor ul.channel li,
.user-examples-target.Chat ul.channel li { display: grid; }
.user-examples-target.Chat ul.channel,
.user-examples-target.ChatMonitor ul.channel {
    display: flex; flex-direction: column-reverse; /* bottom up */
    height: 220px; overflow-y: clip; /* pending pushes up server records */
    padding: 0; /* remove room for bullet in grid layout */
}")