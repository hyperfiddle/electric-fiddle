(ns dustingetz.scratch
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))


(e/defn Scratch []
  (e/client (dom/pre (dom/text "yo"))))
