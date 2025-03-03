(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Form! Input! Unparse OptimisticView]]))

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

;; (def !db nil)
#?(:clj (def !db (atom ()))) ; multiplayer
(e/defn Query-chats [] (e/server (e/diff-by ::id (e/watch !db))))
#?(:clj (defn send-message! [msg] (swap! !db #(take 10 (cons msg %)))))
(defn ->msg-record [id username msg] {::id id ::username username ::msg msg})

(e/defn Query-chats-optimistic [commands]
  (let [server-xs (e/server (Query-chats)) ; reuse server query (differential!)
        client-guesses (Unparse (e/fn [[cmd & args]] ; locally guess command result
                                  (case cmd
                                    `SendMessage (let [[id username msg] args]
                                                   (-> (->msg-record id username msg) ; build record locally
                                                     (assoc ::pending true)))
                                    (e/amb))) commands)]
    ;; reconcile by id
    (OptimisticView ::id (e/Filter some? server-xs) client-guesses)))

(e/defn Channel [msgs]
  (dom/ul (dom/props {:class "channel"})
    (e/for [{:keys [::username ::msg ::id ::pending]} msgs]
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")
                                  :grid-template-columns "1fr 1fr"}})
        (dom/props {:aria-busy pending})
        (dom/span (dom/strong (dom/text username)) (dom/text " " msg))
        (dom/span (dom/props {:style {:text-align "right"}})
          (dom/text (if pending "pending" "ok")))))))

(e/defn SendMessageInput [username]
  (Form! {} ; initial form state
    (e/fn Fields [fields]
      (Input! ::msg "" :disabled (nil? username) :placeholder (if username "Send message" "Login to chat")))
    :show-buttons true ; enter to submit
    :genesis random-uuid ; immediately consume form, ready for next submit, each message get a globaly unique id
    :Parse (e/fn [{msg ::msg} unique-id]
             [`SendMessage unique-id username msg ] ; command - with globaly unique correlation id
             )))

(e/defn ChatApp [username present commands]
  (Present present)
  (dom/hr)
  (e/amb
    (Channel (Query-chats-optimistic commands))
    (SendMessageInput username)
    (Login username)))

(e/defn SendMessage [id username msg]
  (e/server ; secure, validate command here
    (let [record (->msg-record id username msg)] ; secure
      (e/Offload #(try (Thread/sleep 500) ; artificial latency
                       (if false #_true
                         (throw (ex-info "failure" {}))
                         (send-message! record)) ::ok
                    (catch Exception e (doto ::rejected (prn e))))))))

(declare css)
(e/defn ChatMonitor []
  (dom/style (dom/text css))
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        commands (e/with-cycle* first [commands (e/amb)] ; loop in-flight commands – e.g. SendMessage
                (ChatApp username present commands))]
    (prn 'commands (e/as-vec commands))
    (e/for [[t [cmd & args]] commands]
      (let [res (e/server
                  (case cmd
                    `SendMessage (e/apply SendMessage args)
                    (prn ::unmatched-cmd)))]
        (case res
          ::ok (t) ; command accepted, queries will update
          ::rejected (t ::rejected)))))) ; for retry, we're not done yet - todo finish demo

; .user-examples-target.Chat [aria-busy=true] { background-color: yellow; }
(def css "
[aria-busy=true] {background-color: yellow;}
.user-examples-target.ChatMonitor ul.channel li,
.user-examples-target.Chat ul.channel li { display: grid; }
.user-examples-target.Chat ul.channel,
.user-examples-target.ChatMonitor ul.channel {
    display: flex; flex-direction: column;
    justify-content: end; /* bottom up */
    height: 220px; overflow-y: clip; /* pending pushes up server records */
    padding: 0; /* remove room for bullet in grid layout */ }")

