(ns electric-tutorial.chat-monitor
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :as forms :refer [Form! Input! Output SubmitButton! DiscardButton! Reconcile-by]]))

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
#?(:clj (defonce !db (atom ()))) ; multiplayer
(e/defn Query-chats [] (e/server (e/diff-by ::id (sort-by ::created-at (e/watch !db)))))
(defn now! [] #?(:clj (java.util.Date.) :cljs (js/Date.)))
#?(:clj (defn send-message! [msg] (swap! !db #(take 10 (cons msg %)))))
(defn ->msg-record [id username msg]
  {::id id ::username username ::msg msg
   ::created-at (now!)}) ; sort key

(e/defn Message [server-message local-command]
  (Form! (or server-message local-command) ; render message as-is, or guess from command
      (e/fn [{:keys [::username ::msg]}]
        (dom/props {:class "message"})
        (dom/fieldset
          (dom/span (dom/strong (dom/text username)))
          (e/amb
            (Output ::msg msg) ; would use `Input!` for an editable form - out of scope - <p> is valid too
            (SubmitButton! :label "Retry")
            (DiscardButton! :label "x"))))
    :Parse (e/fn [{:keys [::username ::msg]} unique-id] ; form fields + id -> command
             [`SendMessage unique-id username msg])
    :Unparse (when local-command ; must guess
               (e/fn [[_SendMessage id username msg]] [(->msg-record id username msg) id])))) ; opposite of :Parse

(defn add-years [years date] (when date (doto date (.setFullYear (+ years (.getFullYear date))))))

(e/defn Channel [server-messages local-commands]
  (dom/ul (dom/props {:class "channel"})
    (e/for [[record command] (Reconcile-by ::id (fn [[_SendMessage id username msg]] id)
                               ::created-at (fn [_command] (add-years 100 (now!))) ; ensure local pending messages do not interleave with server messages
                               server-messages local-commands)]
      (dom/li (Message record command)))))

(e/defn SendMessageInput [username]
  (Form! nil (e/fn [_] (dom/props {:class "new-message"})
               (Input! ::msg "" :disabled (nil? username) :placeholder (if username "Send message" "Login to chat")))
    :genesis true ; immediately consume form, ready for next submit, each message get a globally unique id
    :tempid random-uuid
    :Parse (e/fn [{:keys [::msg]} unique-id] [`SendMessage unique-id username msg]))) ; command - with globally unique correlation id

(e/defn ChatApp [username present]
  (Present present)
  (dom/hr)
  (e/amb
    (dom/div (dom/props {:class "chat-view"})
      (Channel (e/server (Query-chats)) (SendMessageInput username)))
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
        commands (ChatApp username present)]
    (prn 'commands (e/as-vec commands))
    (e/for [[t [cmd & args]] (e/diff-by first (e/as-vec commands))]
      (let [res (e/server
                  (case cmd
                    `SendMessage (e/apply SendMessage args)
                    (prn ::unmatched-cmd)))]
        (case res
          ::ok (t) ; command accepted, queries will update
          ::rejected (t ::rejected)))))) ; for retry, we're not done yet - todo finish demo

(def css "

.chat-view { display: grid; grid-template-areas: \"channel\" \"new-message\"; overflow: hidden;}

.chat-view .channel {
  grid-area: channel;
  min-height: 230px; /* 10 messages */
  transition: height 0.5s ease;
  z-index: 1;
  display: flex; flex-direction: column; justify-content: end;
  padding: 0;
}

.chat-view .channel li{
  display: block;
  margin: 0;
}

.chat-view .new-message {grid-area: new-message;}

.chat-view .channel form.message:has([aria-busy=true]) {--background-color: yellow;}
.chat-view .channel form.message:has([aria-invalid=true]) {--background-color: pink;}

.chat-view .channel form.message:not(:has([aria-busy=true])) button {display: none;}

.chat-view .channel form.message fieldset {display: contents;}
.chat-view .channel form.message {display: grid; grid-template-columns: auto 1fr auto auto}
.chat-view .channel form.message output {text-align: end; padding: 0 1rem;}

.chat-view .channel form.message {
  height: 23px;
  position:relative;
  --background-color: white;
}

.chat-view .channel form.message:has([aria-busy=true]){
  animation: slide-in 0.5s ease-in forwards;
}

.chat-view .channel form.message::after{ /* background */
   z-index: -1;
   content: \"\";
   position: absolute;
   width: 100%; height: 23px;
   background-color: var(--background-color);
}

@keyframes slide-in {
  from {
    height: 0;
    transform: translateY(1rem) scale(0.99);
    box-shadow: 0 0 1rem lightgray;
  }
}

.channel form {display: unset;} /* address css conflict */
.channel form.message fieldset {background-color: initial;} /* address css conflict */

")
