(ns london-talk-2024.typeahead
  (:require [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(def ul-style {:position "absolute"
               :z-index 2
               :padding 0
               :list-style "none"
               :background-color "white"
               :width "12em"
               :font-size "smaller"
               :border "2px solid black"})

(e/defn Typeahead [v-id Options OptionLabel] ; & [OptionLabel] -- broken
  (dom/div (dom/props {:class "hyperfiddle-typeahead"
                       :style {:position "relative"}})
    (let [#_#_OptionLabel (or OptionLabel (e/fn [x] (pr-str x)))
          !v-id (e/client (atom v-id)) v-id (e/client (e/watch !v-id))
          !search (e/client (atom nil)) search (e/client (e/watch !search))

          t (e/client
              (dom/input
                (dom/props {:placeholder "Filter..."})
                (if-some [t (e/Token (dom/On "focus"))]
                  (do (reset! !search (dom/On "input" #(-> % .-target .-value) "")) t)
                  (dom/props {:value (OptionLabel v-id)}))))] ; controlled only when not focused

      ; neutral
      (if (e/client (some? t)) ; token unserializable
        (dom/ul (dom/props {:style ul-style})
          (e/for [x (Options search)] ; neutral
            (dom/li (dom/props {:style {}})
              (dom/text (OptionLabel x))
              (dom/On "click" (e/client
                                  (fn [e] (doto e (.stopPropagation) (.preventDefault))
                                    (reset! !v-id x) (t))))))))
      v-id)))