(ns staffly.utils
  (:require
   [hyperfiddle.electric-dom3 :as dom]
   [hyperfiddle.electric-forms5 :as forms]
   [hyperfiddle.electric3 :as e]))

(e/defn Table [title columns query Cell]
  (e/client
    (dom/fieldset (dom/props {:class "staffly_utils__table"})
      (let [records (dom/legend (dom/text title " ")
                      (let [search (forms/Input* "")
                            records (e/server (e/Offload #(query search)))]
                        (dom/text (str " (" (e/server (count records)) " items) "))
                        records))]
        (dom/table (dom/props {:style {:--column-count (count columns)}})
          (dom/thead
            (dom/tr
              (e/for [col (e/diff-by {} columns)]
                (dom/th (dom/text col)))))
          (forms/TablePicker* nil (e/server (count records))
            (e/fn [index] (e/server (when-let [e (nth records index nil)]
                                      (e/client (e/for [col (e/diff-by {} columns)]
                                                  (e/server (Cell col e)))))))
            :column-count (count columns)
            :as :tbody))
        (e/amb)))))


(def css
"
.staffly_utils__table legend { margin-left: 1em; font-size: larger; }
"

)