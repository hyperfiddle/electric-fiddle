(ns dustingetz.easy-table
  (:require #?(:clj clojure.java.io)
            [contrib.clojurex :refer [#?(:clj slurp-safe)]]
            [contrib.data :refer [clamp-left]]
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]))

(e/defn TableScroll [record-count Row & [selected]]
  (e/client
    (dom/div (dom/props {:class "Viewport"})
      (let [row-height 24 ; todo parameterize
            [offset limit] (Scroll-window row-height record-count dom/node {})]
        (e/amb ; client
          (dom/table (e/server (dom/props {:style {:--row-height (str row-height "px") :top (str (* offset row-height) "px")}}))
            (let [selected (dom/On "focusin" (fn [event] (-> event .-target (aget "tablescroll-row-id"))) selected)]
              (e/server
                (e/for [i (IndexRing limit offset)] ; render all rows even when record-count < limit
                  (dom/tr (Row i)
                    (dom/props (e/server {:style {:--order (inc i)} :data-row-stripe (mod i 2)}))
                    (dom/props (e/client {:tabindex "0" :aria-selected (= i selected)}))
                    (e/client (aset dom/node "tablescroll-row-id" i)))))
              selected))
          (dom/div (dom/props {:style {:height (str (clamp-left (* row-height (- record-count limit)) 0) "px")}})))))))

(e/defn Load-css [resource-path]
  (dom/style (dom/text (e/server (some-> (clojure.java.io/resource resource-path) slurp-safe)))))

(e/defn EasyTable [title query Row]
  (e/client
    (dom/props {:class "dustingetz-EasyTable"})
    (Load-css "dustingetz/easy_table.css")
    (let [!search (atom "") search (e/watch !search)
          xs! (e/server (query search)), n (e/server (count xs!))]
      (dom/fieldset
        (dom/legend (dom/text title " ")
          (do (reset! !search (Input* "")) (e/amb))
          (dom/text " (" n " items) "))
        (TableScroll n
          (e/fn [i] (e/server (when-some [x (nth xs! i nil)]
                                (Row x)))))))))

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