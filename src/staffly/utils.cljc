(ns staffly.utils
  (:require
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric-forms5 :as forms]
   [hyperfiddle.electric3 :as e]))

(e/defn Table [title query Row]
  (e/client
    (dom/fieldset (dom/props {:class "staffly_utils__table"})
      (let [records (dom/legend (dom/text title " ")
                      (let [search (forms/Input* "")
                            records (e/server (e/Offload #(query search)))]
                        (dom/text (str " (" (e/server (count records)) " items) "))
                        records))]
        (e/server (forms/VirtualScroll :table :tr 24 1 records
                    (e/fn [_index record]
                      (Row record))))))))


(def css
"
.staffly_utils__table legend { margin-left: 1em; font-size: larger; }
"

)