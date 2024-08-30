(ns electric-fiddle.index
  (:require [hyperfiddle :as hf]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/defn Index []
  (e/client
    (dom/h1 (dom/text `Index))
    ;; (dom/pre (dom/text (pr-str r/route)))
    (e/cursor [[k _] (e/diff-by key (sort-by key hf/pages))]
      (dom/div
        (r/link [(list k)] (dom/text (name k)))
        (dom/text " " k)))))
