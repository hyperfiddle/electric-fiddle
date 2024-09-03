(ns dustingetz.hello
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Hello []
  (dom/div (dom/text "Hello")))