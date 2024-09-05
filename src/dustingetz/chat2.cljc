(ns dustingetz.chat2
  (:require [contrib.str :refer [pprint-str]]
            [dustingetz.cqrs0 :as cqrs :refer [PendingController Service]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]
            [electric-tutorial.chat :refer
             [InputSubmit Query-todos #?(:clj !msgs) #?(:clj send-message!)]]))

(e/defn ChatCreate [pending-count]
  (e/amb
    (e/for [[v t] (InputSubmit :placeholder "Type a message" :maxlength 100)]
      (let [id (random-uuid)
            xcmd [::send-msg id v]
            prediction {:db/id id :msg v :t-ms (.now js/Date)}] ; guess time
        [t id xcmd prediction]))
    (dom/text " " pending-count)))

(e/defn ChatView [!msgs]
  (e/with-cycle* first [edits (e/amb)]
    (e/amb
      (dom/ul
        (e/for [x (->> (Query-todos !msgs) (PendingController :db/id edits))]
          (dom/li (dom/props {:style {:visibility (if (some? x) "visible" "hidden")
                                      :background-color (when (::cqrs/pending x) "yellow")}})
            (dom/text (:msg x)))))
      (ChatCreate (e/Count edits)))))

#?(:clj (defn chat-effects [!msgs [cmd id v]] ; secure
          (case cmd
            ::send-msg (m/sp (let [msg {:db/id id :msg v :t-ms (System/currentTimeMillis)}]
                               (m/? (m/sleep 500))
                               (m/? (m/via m/blk (send-message! !msgs msg)))))
            nil)))

(e/defn Stage [edits]
  (dom/pre
    (e/for [[_ _ cmd _] edits]
      (dom/text (pprint-str cmd))))
  edits)

(declare css)

(e/defn Chat2 []
  (e/client
    (dom/props {:class "ChatView"}) (dom/style (dom/text css))
    (Service
      (e/server (partial chat-effects !msgs))
      (Stage
        (ChatView (e/server (identity !msgs)))))))

(def css "
.ChatView ul {
  display: grid; grid-template-rows: repeat(10, 20px);
  grid-auto-flow: dense; align-content: end; height: 200px; }")