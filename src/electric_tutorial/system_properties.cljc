(ns electric-tutorial.system-properties
  (:require [clojure.string :as str]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defn jvm-system-properties [?s]
          (->> (System/getProperties) (into {})
            (filter (fn [[k _v]]
                      (str/includes? (str/lower-case (str k))
                        (str/lower-case (str ?s)))))
            (sort-by first))))

(e/defn SystemProperties []
  (e/client
    (let [!search (atom "") search (e/watch !search)
          system-props (e/server (e/Offload #(jvm-system-properties search)))]
      (dom/div (dom/text (e/server (count system-props)) " matches"))
      (dom/input (dom/props {:type "search", :placeholder "🔎  java.class.path"})
        (reset! !search (dom/On "input" #(-> % .-target .-value) ""))) ; cycle
      (dom/table
        (e/for [[k v] (e/server (e/diff-by key system-props))]
          (println 'rendering k #_v)
          (dom/tr (dom/td (dom/text k)) (dom/td (dom/text v))))))))