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
    (e/for [{:keys [username msg]} msgs] ; render list bottom up in CSS
      (dom/li (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")
                                  :grid-template-columns "max-content auto" :gap "1ch"}})
        (dom/span (dom/strong (dom/text username)) (dom/text " " msg))))))

(e/defn InputSubmitCreate?! ; ?! marks this as an anti-pattern, more rigorous controls upcoming
  [& {:keys [maxlength type parse] :as props
      :or {maxlength 100 type "text" parse identity}}]
  (e/client
    (dom/input (dom/props (-> props (dissoc :parse) (assoc :maxLength maxlength :type type)))
      (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
              (read-clear! [node] (when-some [v (read! node)] (set! (.-value node) "") v))
              (submit! [e] (let [k (.-key e)]
                             (cond
                               (= "Enter" k) (parse (read-clear! (.-target e)))
                               (= "Escape" k) (do (set! (.-value dom/node) "") nil)
                               () nil)))]
        (let [edits (dom/On-all "keydown" submit!)] ; concurrent pending submits
          (dom/props {:aria-busy (pos? (e/Count edits))})
          edits)))))

(e/defn SendMessage [username]
  (let [edits (InputSubmitCreate?! :placeholder (if username "Message" "Login to chat")
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
      (prn 'edits (e/as-vec edits))
      (e/for [[t msg] edits]
        (let [res (e/server
                    (let [record (->msg-record username msg)] ; secure
                      (e/Offload #(try (send-message! record) ::ok
                                    (catch Exception e (doto ::rejected (prn e)))))))]
          (prn 'res res)
          (case res
            ::ok (t) ; sentinel success value
            ::rejected nil ; todo, need more rigorous pattern for failure handling
            ))))
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

; .user-examples-target.Chat [aria-busy=true] { background-color: yellow; }