(ns electric-tutorial.typeahead
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(declare css)
(e/defn Typeahead [v-id Options #_OptionLabel] ; & [OptionLabel] -- broken
  (e/client ; workaround let/drain bug, this shouldn't be needed
    (dom/div (dom/props {:class "hyperfiddle-typeahead"
                         :style {:position "relative"}})
      (dom/style (dom/text css))
      (let [OptionLabel (e/server (or #_OptionLabel (e/fn [x] (pr-str x))))
            !v-id (e/client (atom v-id)) v-id (e/client (e/watch !v-id))
            !search (e/client (atom nil)) search (e/client (e/watch !search))

            t (e/client
                (dom/input (dom/props {:placeholder "Filter..."})
                  (let [[t err] (e/Token (dom/On "focus"))]
                    (if t
                      (reset! !search (dom/On "input" #(-> % .-target .-value) ""))
                      (dom/props {:value (OptionLabel v-id)}))
                    t)))] ; controlled only when not focused

        (e/client ; open and close <ul> instantly (at the cost of site neutrality)
          (if (some? t) ; this if must be on client, but site neutrality is lost
            (dom/ul
              (e/server ; fix sync row load (lost neutrality)
                (e/for [x (Options search)] ; x server
                  (dom/li (dom/text (OptionLabel x)) ; sync row load
                    (dom/On "click" (e/client
                                      (fn [e] (doto e (.stopPropagation) (.preventDefault))
                                        (reset! !v-id x) (t))))))))))
        v-id))))

(def css "
.hyperfiddle-typeahead > ul {
  position: absolute; z-index: 2; padding: 0;
  list-style: none; background-color: white; width: 12em;
  font-size: smaller; border: 1px solid black; }")