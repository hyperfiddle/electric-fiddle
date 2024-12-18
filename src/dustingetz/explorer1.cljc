(ns dustingetz.explorer1
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :as scroll]
            [dustingetz.explorer :as explorer]))

;; e/for on server
;; Despite moving the for's site we observe no perf/latency difference
;; note: body needs to be on client (Row call), otherwise links take an extra round-trip
(e/defn DirectoryExplorer1 []
  (explorer/AppRoot
    (e/fn TableScroll [xs! {:keys [row-height record-count overquery-factor]}]
      (dom/div
        (dom/props {:class "Viewport"})
        (let [[offset limit] (scroll/Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
          (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
            (e/for [i (scroll/IndexRing limit offset)]
              (let [[tab x] (e/server (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children))]
                (e/client
                  (explorer/Row i x tab)))))
          (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}})))))))
