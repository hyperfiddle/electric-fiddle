(ns dustingetz.gridsheet4
  "todo deprecate, use HFQL grid. Used by datomic-browser and folder-explorer"
  #?(:cljs (:require-macros dustingetz.gridsheet4))
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
  (e/server ; todo site neutrality, today requires server bias
    (let [{::keys [Format columns grid-template-columns
                   row-height ; px, same unit as scrollTop
                   page-size #_"tight"]}
          (auto-props props {::row-height 24
                             ::page-size 20
                             ::Format (e/fn [m a] (dom/text #_(e/client) (pr-str (a m))))})
          client-height (* (inc (check number? page-size)) (check number? row-height))
          rows (seq xs)
          row-count (count rows)]
      #_(when rows (check vector? (first rows))) ; ensure treelister call -- can't do here, drain causes token transfer
      #_(check columns) ; "gridsheet: ::columns prop is required"
      (e/client ; why
        (dom/div (dom/props {:role "grid"
                             :class (e/server (::dom/class props))
                             :style (merge (e/server (::dom/style props))
                                      {:height (str client-height "px")
                                       :display "grid" :overflowY "auto"
                                       :grid-template-columns (or (e/server (::grid-template-columns props))
                                                                (->> (repeat (e/server (count columns)) "1fr")
                                                                  (interpose " ") (apply str)))})})
          (e/amb
            (let [[scroll-top] (e/input (scroll-state dom/node))
                  max-height (* row-count row-height)
                  padding-bottom (js/Math.max (- max-height client-height) 0)
                  clamped-scroll-top (js/Math.min scroll-top padding-bottom) ; don't scroll past the end
                  start-row (clojure.math/ceil (/ clamped-scroll-top row-height))
                  ; idea: batch pagination to improve latency (does reducing network even help or just making loads happen offscreen?)
                  start-row-page-aligned (round-floor start-row page-size) ; clamp start to the nearest page

                  [col1 & col-tail] columns, col-tail (e/diff-by identity col-tail)]
              #_(println [:scrollTop scroll-top :scrollHeight scroll-height :clientHeight client-height
                          :padding-bottom padding-bottom
                          :start-row start-row :start-row-page-aligned start-row-page-aligned
                          :take page-size :max-height max-height])

              (e/for [k (e/amb col1 col-tail)]
                (dom/div (dom/props {:role "columnheader"
                                     :style {:position "sticky" #_"fixed" :top (str 0 "px")
                                             :background-color "rgb(248 250 252)" :box-shadow "0 1px gray"}})
                  (dom/text (name k))))

              (e/amb
                (let [xs! (e/server rows ; fixme why is this touch necessary?
                            (vec (->> rows (drop start-row) (take page-size))))]
                  (e/for [i (e/diff-by identity (range page-size))]
                    (let [xx (e/server (get xs! i [0 nil]))
                          indent-depth (e/server (nth xx 0))
                          record (e/server (nth xx 1))] ; record can be any shape
                      (dom/div (dom/props {:role "group" :style {:display "contents"
                                                                 :grid-row (inc i)}})
                        (e/amb
                          (dom/div (dom/props {:role "gridcell"
                                               :style {:padding-left (-> indent-depth (* 15) (str "px"))
                                                       :position "sticky" :top (str (* row-height (inc i)) "px")
                                                       :height (str row-height "px")}})
                            (Format record col1))
                          (e/for [col col-tail]
                            (dom/div (dom/props {:role "gridcell"
                                                 :style {:position "sticky" :top (str (* row-height (inc i)) "px")
                                                         :height (str row-height "px")}})
                              (Format record col))))))))
                (dom/div (dom/props {:style {:padding-bottom (str padding-bottom "px")}})))) ; scrollbar
            (dom/div (dom/text (pr-str {:count row-count})))))))))

(e/defn Explorer [query-fn props]
  (e/client
    (let [search (or r/route "")
          [t {search' ::search}] (Input! ::search search :type "search" :placeholder "Search")
          search (if (e/Some? search') search' search)]
      (when (e/Some? t) (case (r/ReplaceState! ['. search']) (t)))
      (dom/hr)
      (doto
        (GridSheet (e/server (e/Offload #(query-fn search))) props)
        (prn 'Explorer-ret)))))

; Ideas:
; userland could format the row, no need
; for grid to be aware of columns, it's just vertical scroll.
; horizontal scroll changes things.
; except for the tricky styles ...