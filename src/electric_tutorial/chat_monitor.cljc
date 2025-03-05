(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :as forms :refer [Form! Input! Output Button! OptimisticView Checkbox]]))

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

(e/defn Unparse [[cmd & args]] ; locally guess command result
  (case cmd
    `SendMessage (let [[id username msg] args] (->msg-record id username msg))
    (e/amb)))

(e/defn Message [record]
  (forms/TrackTx ; ugly, should come for free
    (e/fn [err]
      (Form! ; models an entity
          (::forms/command record) ; nil for server message
          (e/fn [{:keys [::username ::msg] :as record}]
            (dom/props {:class "message"})
            (dom/fieldset
              (dom/props {:disabled true}) ; read-only entity - messages are not editable in this demo
              (dom/span (dom/strong (dom/text username)))
              (Output ::msg msg))) ; would be `Input!` for an editable message - see HTML's <output>
        :show-buttons (some? err)
        :tempid random-uuid
        :Unparse (e/fn [command] [record (::id record)])
        :Parse (e/fn [{:keys [::username ::msg]} unique-id] [`SendMessage unique-id username msg]))))
  )

(e/defn Channel [server-messages commands]
  (dom/ul (dom/props {:class "channel"})
          (e/for [record (forms/Reconcile-by ::id Unparse server-messages commands)]
            (dom/li (Message record)))))

(e/defn SendMessageInput [username]
  (Form! {} ; initial form state
      (e/fn Fields [fields]
        (Input! ::msg "" :disabled (nil? username) :placeholder (if username "Send message" "Login to chat")))
    :genesis true ; immediately consume form, ready for next submit, each message get a globaly unique id
    :tempid random-uuid
    :Parse (e/fn [{msg ::msg} unique-id] [`SendMessage unique-id username msg]))) ; command - with globaly unique correlation id

(e/defn ChatApp [username present commands]
  (Present present)
  (dom/hr)
  (e/amb
    (Channel (e/server (Query-chats)) (SendMessageInput username))
    (Login username)))

(def next-toggle (partial swap! (atom false) not))

(e/defn SendMessage [id username msg]
  (e/server ; secure, validate command here
    (let [record (->msg-record id username msg)] ; secure
      (e/Offload #(try (Thread/sleep 1000) ; artificial latency
                       (if (next-toggle)
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

.channel form[aria-busy=true] {background-color: yellow;}
.channel form[aria-invalid] {background-color: red;}
.channel form.message fieldset {display: contents;}
.channel form.message {display: grid; grid-template-columns: auto 1fr auto auto}
.channel form.message output {text-align: end; padding: 0 1rem;}

.channel form.message fieldset:disabled input {display:none;}
.channel form.message fieldset:not(:disabled) output {display:none;}


.channel form.message fieldset:disabled input:readonly{  }

.user-examples-target.ChatMonitor ul.channel li,
.user-examples-target.Chat ul.channel li { display: grid; }
.user-examples-target.Chat ul.channel,
.user-examples-target.ChatMonitor ul.channel {
    display: flex; flex-direction: column;
    justify-content: end; /* bottom up */
    height: 220px; overflow-y: clip; /* pending pushes up server records */
    padding: 0; /* remove room for bullet in grid layout */ }


.channel form {display: unset;} /* address css conflict */
.channel form.message fieldset {background-color: initial;} /* address css conflict */



")
