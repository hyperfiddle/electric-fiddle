(ns electric-fiddle.index
  (:require [hyperfiddle :as hf]
            [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]
            #_[hyperfiddle.router :as r]))

(e/defn Index []
  (e/client
    (dom/h1 (dom/text `Index))
    (dom/pre (dom/text #_(pr-str r/route)))
    (e/for-by key [[k _] (sort hf/pages)]
      (dom/div
        #_(r/link [(list k)] (dom/text (name k)))
        (dom/text " " k)))))
