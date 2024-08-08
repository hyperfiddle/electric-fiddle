(ns dustingetz.scratch
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))


(e/defn Scratch []
  (e/client (dom/pre (dom/text "yo"))))
