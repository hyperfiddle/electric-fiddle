(ns london-talk-2024.two-clocks
  (:require
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn TwoClocks []
  (e/client
    (let [c (e/client (e/SystemTimeMs))
          s (e/server (e/SystemTimeMs))]

      (dom/div (dom/text "client time: " c))
      (dom/div (dom/text "server time: " s))
      (dom/div (dom/text "difference: " (- s c))))))
