(ns london-talk-2024.two-clocks
  (:require
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn TwoClocks []
  (e/client
    (let [c (e/client (e/System-time-ms))
          s (e/server (e/System-time-ms))]

      (dom/div (dom/text "client time: " c))
      (dom/div (dom/text "server time: " s))
      (dom/div (dom/text "difference: " (- s c))))))
