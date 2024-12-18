(ns dustingetz.explorer2
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :as scroll]
            [dustingetz.explorer :as explorer]))

;; Absolute index (`i`) on client, data comes later from server.
;; Means order changes immediately, data afterwards.
;; I.e. scrolled data is stale until new data arrives.
(e/defn DirectoryExplorer2 []
  (explorer/AppRoot
    (e/fn TableScroll [xs! {:keys [row-height record-count overquery-factor]}]
      (dom/div
        (dom/props {:class "Viewport"})
        (e/client
          (let [[offset limit] (scroll/Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
            (dom/table (dom/props {:style {:position "sticky" :top 0} #_{:position "relative" :top (str (* offset row-height) "px")}})
              (e/for [i (scroll/IndexRing limit offset)]
                (let [[tab x] (e/server (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children))]
                  #_(e/client) ; no difference, janky both ways
                  (explorer/Row i x tab))))
            (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}}))))))))
