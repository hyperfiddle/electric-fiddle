(ns electric-examples.reactive-collections
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [clojure.string :as str]))

#?(:clj (defn jvm-system-properties [search-str]
          (->> (System/getProperties) (into {})
            (filter (fn [[key _value]]
                      (str/includes? (str/lower-case (str key))
                        (str/lower-case (str search-str)))))
            (sort-by first))))

(e/defn ReactiveCollections []
  (e/client
    (let [search (dom/input (dom/props {:type "search", :placeholder "🔎  java.class.path"})
                            (dom/On "input" (fn [event] (-> event .-target .-value)) ""))
          system-props (e/server (e/Offload #(jvm-system-properties search)))]
      (dom/div (dom/text (e/server (count system-props)) " matches"))
      (dom/table
        (e/for [[key value] (e/server (e/diff-by key system-props))]
          (println 'rendering key)
          (dom/tr (dom/td (dom/text key))
                  (dom/td (dom/props {:style {:white-space :nowrap}})
                    (dom/text value))))))))