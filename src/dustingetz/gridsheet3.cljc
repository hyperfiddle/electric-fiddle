(ns dustingetz.gridsheet3
  "todo deprecate, use HFQL grid. Used by datomic-browser and folder-explorer"
  #?(:cljs (:require-macros dustingetz.gridsheet3))
  (:require clojure.math
            [contrib.assert :refer [check]]
            [contrib.data :refer [auto-props round-floor]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input Input!]]
            [hyperfiddle.router3 :as r] ; todo remove
            #?(:cljs [london-talk-2024.dom-scroll-helpers :refer [scroll-state resize-observer]])
            #?(:cljs goog.object)))

(e/defn GridSheet [xs props]
  #_(e/server) ; todo site neutrality, today requires server bias
  (let [{::keys [Format columns grid-template-columns
                 row-height ; px, same unit as scrollTop
                 page-size #_"tight"]}
        (auto-props props {::row-height 24
                           ::page-size 20
                           ::Format (e/fn [m a] (dom/text #_(e/client) (pr-str (a m))))})
        client-height (* (inc (check number? page-size)) (check number? row-height))
        rows (seq xs)
        row-count (count rows)]
    #_(check columns) ; "gridsheet: ::columns prop is required"
    (e/client
      (dom/div (dom/props {:role "grid"
                           :class (e/server (::dom/class props))
                           :style (merge (e/server (::dom/style props))
                                    {:height (str client-height "px")
                                     :display "grid" :overflowY "auto"
                                     :grid-template-columns (or (e/server (::grid-template-columns props))
                                                              (->> (repeat (e/server (count columns)) "1fr")
                                                                (interpose " ") (apply str)))})})
        (let [[scroll-top] (e/input (scroll-state dom/node))
              max-height (* row-count row-height)
              padding-bottom (js/Math.max (- max-height client-height) 0)

              ; don't scroll past the end
              clamped-scroll-top (js/Math.min scroll-top padding-bottom)

              start-row (clojure.math/ceil (/ clamped-scroll-top row-height))

              ; batch pagination to improve latency
              ; (does reducing network even help or just making loads happen offscreen?)
              ; clamp start to the nearest page
              start-row-page-aligned (round-floor start-row page-size)

              [col1 & col-tail] columns, col-tail (e/diff-by identity col-tail)]
          #_(println [:scrollTop scroll-top :scrollHeight scroll-height :clientHeight client-height
                      :padding-bottom padding-bottom
                      :start-row start-row :start-row-page-aligned start-row-page-aligned
                      :take page-size :max-height max-height])

          (e/for [k (e/diff-by identity columns)]
            (dom/div (dom/props {:role "columnheader"
                                 :style {:position "sticky" #_"fixed" :top (str 0 "px")
                                         :background-color "rgb(248 250 252)" :box-shadow "0 1px gray"}})
              (dom/text (name k))))

          ; userland could format the row, no need
          ; for grid to be aware of columns, it's just vertical scroll.
          ; horizontal scroll changes things.
          ; except for the tricky styles ...
          (e/server
            (when (seq rows) (check vector? (first rows)))
            (let [xs (vec (->> rows (drop start-row) (take page-size)))]
              (e/for [i (e/diff-by identity (range page-size))]
                (let [[depth m] (get xs i [0 ::empty])]
                  (e/client
                    (dom/div (dom/props {:role "group" :style {:display "contents"
                                                               :grid-row (inc i)}})
                      (dom/div (dom/props {:role "gridcell"
                                           :style {:padding-left (-> depth (* 15) (str "px"))
                                                   :position "sticky" :top (str (* row-height (inc i)) "px")
                                                   :height (str row-height "px")}})
                        (e/server (case m ::empty nil (Format m col1)))) ; for effect
                      (e/for [a col-tail]
                        (dom/div (dom/props {:role "gridcell"
                                             :style {:position "sticky" :top (str (* row-height (inc i)) "px")
                                                     :height (str row-height "px")}})
                          (e/server (case m ::empty nil (Format m a))))))))))) ; for effect
          (dom/div (dom/props {:style {:padding-bottom (str padding-bottom "px")}})))) ; scrollbar
      (dom/div (dom/text (pr-str {:count row-count}))))))

(e/defn Explorer [query-fn props]
  (e/client
    #_(dom/pre (dom/text (pr-str (r/Route-at ['.]))))
    #_(r/focus [:search])
    (let [search (or r/route "")
          [t {search' ::search}] (Input! ::search search :type "search" :placeholder "Search")
          search (if (e/Some? search') search' search)]
      (when (e/Some? t) (case (r/ReplaceState! ['. search']) (t)))
      (dom/hr)
      (e/server
        (GridSheet (e/Offload #(query-fn search)) props)))))