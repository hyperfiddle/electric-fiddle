(ns london-talk-2024.typeahead
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Typeahead [v-id Options & [OptionLabel]]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"
                         :style {:position "relative"}})
      (let [OptionLabel (or OptionLabel (e/fn [x] (pr-str x)))
            container dom/node
            !v-id (atom v-id) v-id (e/watch !v-id)]
        (dom/input
          (dom/props {:placeholder "Filter..."})
          (if-some [close! ($ e/Token ($ dom/On "focus"))]
            (let [search ($ dom/On "input" #(-> % .-target .-value) "")]
              (binding [dom/node container] ; portal
                (dom/ul (dom/props {:style {:position "absolute"
                                            :z-index 2
                                            :padding 0
                                            :list-style "none"
                                            :background-color "white"
                                            :width "12em"
                                            :font-size "smaller"
                                            :border "2px solid black"}})
                  (e/for [id ($ Options search)]
                    (dom/li (dom/props {:style {}})
                      (dom/text ($ OptionLabel id))
                      ($ dom/On "click" (fn [e]
                                          (doto e (.stopPropagation) (.preventDefault))
                                          (reset! !v-id id) (close!))))))))
            (dom/props {:value ($ OptionLabel v-id)}))) ; controlled only when not focused
        v-id))))