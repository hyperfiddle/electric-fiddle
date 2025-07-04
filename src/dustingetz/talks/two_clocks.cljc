(ns dustingetz.talks.two-clocks "http://localhost:8080/dustingetz.talks.two-clocks!TwoClocks/"
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn TwoClocks []
  (e/client
    (let [s (e/server (e/System-time-ms))
          c (e/client (e/System-time-ms))]

      (dom/div (dom/text "server time: " s))
      (dom/div (dom/text "client time: " c))
      (dom/div (dom/text "skew: " (- s c))))))