(ns electric-tutorial.table-raster
  (:require
    [contrib.data :refer [clamp-left]]
    [dustingetz.str :refer [abc-seq]]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.electric-scroll0 :refer [Raster Scroll-window]]
    [hyperfiddle.electric3 :as e]))

(def row-height 24)

(e/defn VirtualScroll [xs!]
  (dom/div (dom/props {:class "Viewport"})
    (let [row-count (e/server (count xs!))
          [offset limit] (Scroll-window row-height row-count dom/node {:overquery-factor 1})
          occluded-rows-height (clamp-left (* row-height (- row-count limit)) 0)]
      (dom/props {:style {:--offset offset :--row-height (str row-height "px")}})
      (dom/table
        (e/for [i (Raster offset limit)]
          (let [x (e/server (nth xs! i nil))]
            (dom/tr (dom/text x)))))
      (dom/div (dom/props {:style {:height (str occluded-rows-height "px")}})))))

(declare css)
(e/defn TableRaster []
  (dom/style (dom/text css))
  (dom/div (dom/props {:class "App"})
    (VirtualScroll (e/server (abc-seq 1 500)))))

(def css "
.App { height: calc(24px * 20); border: 1px solid black; }
.Viewport { height: 100%; overflow-x: hidden; overflow-y: auto; }
.Viewport table tr { height: var(--row-height); }
.Viewport table { position: relative; top: calc(var(--offset) * var(--row-height)); }")
