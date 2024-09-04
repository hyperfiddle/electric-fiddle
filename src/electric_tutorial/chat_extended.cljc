(ns electric-tutorial.chat-extended
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defonce !msgs (atom (list)))) ; server only state
#?(:clj (defonce !present (atom {}))) ; session-id -> user

(e/defn Presence! [username]
  (e/server
    (let [session-id (get-in e/http-request [:headers "sec-websocket-key"])]
      (swap! !present assoc session-id username)
      (e/on-unmount #(swap! !present dissoc session-id)))
    (e/diff-by key (e/watch !present))))

(e/defn ChatExtended []
  (let [username (e/server (get-in e/http-request [:cookies "username" :value]))
        present (Presence! username)]

    (dom/div (dom/text "Present: " (e/Count present)))
    (dom/ul
      (e/for [[_ username] present]
        (dom/li (dom/text username))))

    (dom/hr)
    (dom/ul
      (e/for [{::keys [::username ::msg]} (e/server (e/diff-by ::message-id (reverse (e/watch !msgs))))]
        (dom/li (dom/strong (dom/text username))
          (dom/text " " msg))))

    (let [pending (dom/input (dom/props {:placeholder "Type a message" :maxlength 100 :disabled (nil? username)})
                    (dom/OnAll "keydown" #(when (= "Enter" (.-key %))
                                            (when-some [v! (not-empty (-> % .-target .-value))] ; untrusted
                                              (set! (.-value dom/node) "")
                                              (.slice v! 0 100)))))]
      (e/for [[v t] pending]
        (let [m {::message-id (random-uuid) ::username username ::msg v}]
          (t (e/server ({} (swap! !msgs #(take 10 (cons m %))) nil)))))

      (dom/props {:style {:background-color (when (pos? (e/Count pending)) "yellow")}})
      (dom/text " " (e/Count pending)))

    (dom/div
      (if (some? username)
        (dom/text "Authenticated as: " username)
        (dom/a (dom/props {:href "/auth"}) (dom/text "Set login cookie (blank password)"))))))