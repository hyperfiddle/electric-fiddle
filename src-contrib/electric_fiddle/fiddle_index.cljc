(ns electric-fiddle.fiddle-index
  (:require [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/defn FiddleIndex []
  (e/client
    (dom/h1 (dom/text `FiddleIndex))
    ;; (dom/pre (dom/text (pr-str r/route)))
    (e/cursor [[k _] (e/diff-by key (sort-by key hf/pages))]
      (dom/div
        (r/link [k] (dom/text (name k)))
        (dom/text " " k)))))
