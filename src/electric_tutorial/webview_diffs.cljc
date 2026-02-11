(ns electric-tutorial.webview-diffs
  (:require #?(:clj [dustingetz.teeshirt-orders-datascript :refer [ensure-db!]])
            [electric-tutorial.webview2 :refer [Teeshirt-orders Row]]
            [electric-tutorial.webview-column-picker :refer [ColumnPicker]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn GenericTable [cols Query Row]
  (let [ids (Query)]
    (dom/table (dom/props {:style {:--colcount (e/Count cols)}})
      (e/for [id (e/Tap-diffs ids)] ; here -- row diffs
        (dom/tr
          (let [m (Row id)]
            (e/for [k (e/Tap-diffs cols)] ; here -- column diffs
              (dom/td
                (e/call (get m k))))))))))

(declare css)
(e/defn WebviewDiffs []
  (e/client
    (dom/style (dom/text css))
    (let [!log (atom [])
          db (e/server (ensure-db!))
          colspec (dom/div (ColumnPicker [:db/id :order/email :order/gender :order/shirt-size]))
          search (dom/input (dom/On "input" #(-> % .-target .-value) ""))]
      (binding [e/Tap-diffs (e/Partial e/Tap-diffs #(swap! !log conj %))]
        (GenericTable
          colspec
          (e/Partial Teeshirt-orders db search nil)
          (e/Partial Row db)))
      (dom/hr)
      (dom/button (dom/text "clear log") (dom/On "click" (fn [_] (reset! !log [])) nil))
      (dom/pre
        (e/for [x (e/diff-by hash (reverse (e/watch !log)))] ; {} means ordinal diff-by
          (dom/div (dom/text (pr-str x))))))))

(def css "
.user-examples-target table { display: grid; column-gap: 1ch;
  grid-template-columns: repeat(var(--colcount), max-content);
  grid-template-rows: repeat(3, 24px); }
.user-examples-target tr { display: contents; }
.user-examples-target { height: 30em; }
.user-examples-target pre { align: bottom; }")