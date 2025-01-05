(ns dustingetz.easy-table
  (:require #?(:clj clojure.java.io)
            [contrib.clojurex :refer [#?(:clj slurp-safe)]]
            [contrib.data :refer [clamp-left]]
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]))

(e/defn TableScroll [record-count Row]
  #_(e/server)
  (dom/div (dom/props {:class "Viewport"})
    (let [row-height 24 ; todo parameterize and inject css var
          [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
      (dom/table (dom/props {:style {:--row-height (str row-height "px")
                                     :position "relative" :top (str (* offset row-height) "px")}})
        (e/for [i (IndexRing limit offset)] ; render all rows even with fewer elements
          (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
            (Row i))))
      (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                  (* row-height (- record-count limit)) 0) "px")}})))))

(e/defn Load-css [resource-path]
  (dom/style (dom/text (e/server (some-> (clojure.java.io/resource resource-path) slurp-safe)))))

(e/defn EasyTable [title query Row]
  (e/client
    (dom/props {:class "dustingetz-EasyTable"})
    (Load-css "dustingetz/easy_table.css")
    (let [!search (atom "") search (e/watch !search)]
      (let [xs! (e/server (query search))
            n (e/server (count xs!))]
        (dom/fieldset
          (dom/legend
            (dom/text title " ")
            (do (reset! !search (Input* "")) (e/amb))
            (dom/text " (" n " items) "))
          (e/server ; user controls TableScroll e/for site
            (TableScroll n
              (e/fn [i]
                (when-some [x (nth xs! i nil)] ; fixme no overscroll
                  ; beware glitched nil pass through
                  (Row x))))))))))

;; Example integration

(declare css)
(e/defn DemoEasyTable []
  (e/client (dom/props {:class "DemoEasyTable"}) (dom/style (dom/text css))
    (EasyTable "DemoEasyTable"
      (e/server (fn query [search]
                  (vec (->> (range 4000)
                         (filter #(includes-str? % search))))))
      (e/fn Row [x] (dom/td (dom/text (pr-str x)))))))

(def css "
.DemoEasyTable fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }
.DemoEasyTable table { grid-template-columns: auto; }")
