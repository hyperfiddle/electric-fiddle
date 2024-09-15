(ns electric-tutorial.chat
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

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
    (e/for [{:keys [username msg]} msgs]
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")
                                  :grid-template-columns "max-content auto" :gap "1ch"}})
        (dom/span (dom/strong (dom/text username)) (dom/text " " msg))))))

(e/defn InputSubmit! [& {:keys [maxlength] :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
              (read-clear! [node] (when-some [v (read! node)] (set! (.-value node) "") v))
              (submit! [e] (when (= "Enter" (.-key e)) (read-clear! (.-target e))))]
        (dom/OnAll "keydown" submit!)))))

(e/defn SendMessage [username]
  (let [edits (InputSubmit! :placeholder (if username "Message" "Login to chat")
                  :maxlength 100 :disabled (nil? username))]
    (dom/text " " (e/Count edits))
    edits))

(e/defn Query-chats [!db] (e/server (e/diff-by :db/id (e/watch !db))))
#?(:clj (defn send-message! [!db msg] (swap! !db #(take 10 (cons msg %)))))
#?(:clj (defonce !db (atom (repeat 10 nil))))
#?(:clj (defonce !present (atom {}))) ; session-id -> user

(defn msg-ctor [username msg] {:db/id (random-uuid) :username username :msg msg})

(e/defn Chat []
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (e/server (Presence! !present username))
        msgs (e/server (Query-chats !db))]
    (e/amb ; workaround crash, fixme
      (Present present)
      (dom/hr)
      (Channel msgs)
      (let [edits (SendMessage username)]
        (dom/props {:style {:background-color (when (pos? (e/Count edits)) "yellow")}})
        (e/for [[t v] edits]
          (case (e/server
                  (let [msg (msg-ctor username v)] ; secure
                    (case (e/Offload #(send-message! !db msg)) ::ok)))
            ::ok (t))))
      (Login username))))