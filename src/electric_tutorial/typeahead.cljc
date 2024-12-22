(ns electric-tutorial.typeahead
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(declare css)
(e/defn Typeahead [v-id Options #_OptionLabel]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"})
      (dom/style (dom/text css))
      (let [OptionLabel (e/server (or #_OptionLabel (e/fn [x] (pr-str x))))
            !v-id (atom v-id) v-id (e/watch !v-id)
            !search (atom nil) search (e/watch !search)

            t (dom/input (dom/props {:placeholder "Filter..."})
                (let [[t err] (e/Token (dom/On "focus" identity nil))]
                  (if t
                    (reset! !search (dom/On "input" #(-> % .-target .-value) ""))
                    (dom/props {:value (OptionLabel v-id)}))
                  t))] ; controlled only when not focused

        (if (some? t)
          (dom/ul
            (e/server
              (e/for [x (Options search)] ; x server
                (dom/li (dom/text (OptionLabel x))
                  (dom/On "click" (e/client ; dom/On will auto-site, but cc/fn doesn't transfer
                                    (fn [e] (doto e (.stopPropagation) (.preventDefault))
                                      (reset! !v-id x) (t))) nil))))))
        v-id))))

(def css "
.hyperfiddle-typeahead { position: relative; }
.hyperfiddle-typeahead > ul {
  position: absolute; z-index: 2; padding: 0;
  list-style: none; background-color: white; width: 12em;
  font-size: smaller; border: 1px solid black; }")