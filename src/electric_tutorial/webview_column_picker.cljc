(ns electric-tutorial.webview-column-picker
  (:require #?(:clj [dustingetz.teeshirt-orders-datascript :refer [ensure-db!]])
            [electric-tutorial.webview2 :refer [Teeshirt-orders Row]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Checkbox*]]))

(e/defn GenericTable [cols Query Row]
  (let [ids (Query)]
    (dom/table (dom/props {:style {:--colcount (e/Count cols)}}) ; css var
      (e/for [id ids]
        (dom/tr
          (let [m (Row id)]
            (e/for [k cols]
              (dom/td
                (e/call (get m k))))))))))

(e/defn ColumnPicker [cols]
  (e/client
    (->> (e/for [col cols]
           [col (Checkbox* true :label col)]) ; reactive ui w/ inline dom!
      e/as-vec ; materialize clojure vector from diffs
      (filter (fn [[col checked]] checked))
      (map first) sort (e/diff-by identity)))) ; unmaterialize

(declare css)
(e/defn WebviewColumnPicker []
  (e/client
    (dom/style (dom/text css))
    (let [db (e/server (ensure-db!))
          colspec (dom/div (ColumnPicker (e/amb :db/id :order/email :order/gender :order/shirt-size)))
          search (dom/input (dom/On "input" #(-> % .-target .-value) ""))]
      (GenericTable
        colspec
        (e/Partial Teeshirt-orders db search nil)
        (e/Partial Row db)))))

(def css "
.user-examples-target table { display: grid; column-gap: 1ch;
  grid-template-columns: repeat(var(--colcount), max-content);
  grid-template-rows: repeat(3, 24px); }
.user-examples-target tr { display: contents; }
.user-examples-target pre { align: bottom; }")