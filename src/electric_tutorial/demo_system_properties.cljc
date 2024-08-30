(ns electric-tutorial.demo-system-properties
  (:require [clojure.string :as str]
            [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj
   (defn jvm-system-properties [?s]
     (->> (System/getProperties)
       (into {})
       (filter (fn [[k _v]]
                 (str/includes? (str/lower-case (str k))
                   (str/lower-case (str ?s)))))
       (sort-by first))))

(e/defn SystemProperties []
  (let [!search (atom "")
        search (e/watch !search)
        system-props (e/server ($ e/Offload #(jvm-system-properties search)))
        matched-count (e/server (count system-props))]
    (dom/div (dom/text matched-count " matches"))
    (dom/input
      (dom/props {:type "search", :placeholder "🔎  java.class.path"})
      (reset! !search ($ dom/On "input" #(-> % .-target .-value) "")))
    (dom/table
      (dom/tbody
        ;; Collection diffed on server, stabilized with "react key"
        (e/cursor [[k v] (e/server (e/diff-by key system-props))]
          (println 'rendering k #_v)
          (dom/tr (dom/td (dom/text k)) (dom/td (dom/text v))))))))
