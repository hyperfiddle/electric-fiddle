(ns dustingetz.talks.chat-simple-scroll
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window]]))

(declare query)

(e/defn ChatApp [row-height record-count]
  (e/client
    (dom/ul
      (let [[offset limit] (Scroll-window row-height record-count dom/node {})
            records! (e/server (query '...))]
        (e/for [i (e/diff-by identity (range offset limit))]
          (let [record (e/server (nth records! i nil))]
            (dom/li (dom/text (:message record)))))))))