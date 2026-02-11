(ns dustingetz.talks.chat-simple
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(declare query)

(e/defn Channel-messages [db]
  (e/server (e/diff-by identity
              (query '...))))

(e/defn ChatApp [db]
  (e/client
    (dom/ul
      (e/for [message (Channel-messages db)]
        (dom/li (dom/text message))))))