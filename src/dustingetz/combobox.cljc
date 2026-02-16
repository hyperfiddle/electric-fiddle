(ns dustingetz.combobox
  (:require [contrib.data :refer [auto-props]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric3 :as e]))

; https://github.com/tailwindlabs/headlessui/blob/main/packages/%40headlessui-react/src/components/combobox/combobox.tsx

(e/defn ComboBox-item
  [& {:keys [label selected? value!] ; client sited
      :or {label "Leslie Alexander"
           selected? true
           value! #()}}]
  (e/client
    (dom/li (dom/props {:id "option-0" :role "option" :tabindex "-1"})
      (dom/span (dom/text label)
        (when selected? (dom/props {:class "font-semibold"})))
      (dom/On "click" (fn [e] (doto e (.stopPropagation) (.preventDefault)) (value!)) nil))))

(e/defn ComboBox [v-record & {:as props}]
  (let [{::keys [Options Option-label]} ; props sited by user
        (auto-props props
          {::Options (e/fn [search] (e/amb {:user/name "Alice"} {:user/name "Bob"}))
           ::Option-label (e/fn [x] (:user/name x))})] ; serializable for convenience, note no dom/text
    (e/client
      (dom/div (dom/props {:class "hyperfiddle-typeahead"})
        (let [!v-record (atom v-record) v-record (e/watch !v-record)
              !search (atom nil) search (e/watch !search)
              options-id (str (gensym "id-"))]

          (if-some [t (dom/input
                        (dom/props {:type "text" :role "combobox" :placeholder "Filter..."
                                    :aria-controls options-id :aria-expanded "false"})
                        (let [[t err] (e/Token (e/Filter some? (dom/On "focus" identity nil)))]
                          (if t ; controlled only when not focused
                            (reset! !search (dom/On "input" #(-> % .-target .-value) ""))
                            (dom/props {:value (some-> v-record Option-label)}))
                          t))]
            (dom/ul (dom/props {:id options-id :role "listbox"})
              (e/for [x (Options search)] ; assume serializable record, otherwise need user identity
                (ComboBox-item
                  :label (Option-label x) ; sync row load
                  :selected? (= x v-record) ; by value, to avoid user boilerplate to define identity
                  :value! (fn [] (reset! !v-record x) (t))))))
          v-record)))))