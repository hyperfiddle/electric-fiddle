(ns electric-tutorial.demo-chat-extended
  (:require [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(defonce !msgs #?(:cljs nil, :clj (atom (list)))) ; server only state
(defonce !present #?(:cljs nil, :clj (atom {}))) ; session-id -> user10M

(e/defn Chat-UI [username]
  (e/client
    (dom/div (dom/text "Present: "))
    (dom/ul
      (e/cursor [[session-id username] (e/server (e/diff-by first (e/watch !present)))]
        (dom/li (dom/text username (str " (session-id: " session-id ")")))))

    (dom/hr)
    (dom/ul
      (e/cursor [{::keys [username msg]} (e/server (e/diff-by ::message-id (reverse (e/watch !msgs))))]
        (dom/li (dom/strong (dom/text username))
                (dom/text " " msg))))

    (dom/input
      (dom/props {:placeholder "Type a message" :maxlength 100})
      (e/cursor [[msg spend!] ($ dom/OnAll "keydown"
                                 #(when (= "Enter" (.-key %))
                                    (when-some [v (not-empty (.slice (-> % .-target .-value) 0 100))]
                                      (set! (.-value dom/node) "")
                                      v)))]
        (spend! (e/server (swap! !msgs #(cons {::message-id (random-uuid) ::username username ::msg msg}
                                          (take 9 %)))))))))

(e/defn ChatExtended []
  (let [session-id (e/server (get-in e/http-request [:headers "sec-websocket-key"]))
        username   (e/server (get-in e/http-request [:cookies "username" :value]))]
    (if-not (some? username)
      (dom/div
        (dom/text "Set login cookie here: ")
        (dom/a (dom/props {:href "/auth"}) (dom/text "/auth"))
        (dom/text " (blank password)"))
      (do
        (e/server
          (swap! !present assoc session-id username)
          (e/on-unmount #(swap! !present dissoc session-id)))
        (dom/div (dom/text "Authenticated as: " username))
        ($ Chat-UI username)))))
