(ns dustingetz.explorer3
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :as scroll]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [dustingetz.explorer :as explorer]))

;; Absolute index on client, data comes later from server.
;; Means order changes immediately, data afterwards.
;; Scrolled data is stale until new data arrives. We add visibility:hidden
;; to the scrolled rows, then the rows look empty and the glitch is gone.
;; But this damages performance under no latency.

(e/defn Unglitch [x else]
  (let [[value clock] (e/with-cycle [[p c] [::init 0]]
                        [x (if (= p x) c (inc c))])]
    (e/Reconcile (if (= clock (e/server (identity clock))) value else))))

(e/defn Row [i x tab visible?]
  (dom/tr (dom/props {:style {:--order (inc i), :--visibility visible?}})
    (dom/td (explorer/Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
    (dom/td (explorer/Render-cell x ::fs/modified))
    (dom/td (explorer/Render-cell x ::fs/size))
    (dom/td (explorer/Render-cell x ::fs/kind))))

(e/defn DirectoryExplorer3 []
  (explorer/AppRoot
    (e/fn TableScroll [xs! {:keys [row-height record-count overquery-factor]}]
      (dom/div
        (dom/props {:class "Viewport"})
        (e/client
          (let [[offset limit] (scroll/Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
            (dom/table
              (dom/props {:style {:top (str (* offset row-height) "px")}})
              (e/for [i (scroll/IndexRing limit offset)]
                (let [[tab x] (e/server (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children))]
                  (Row i x tab (Unglitch i "hidden")))))
            (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}}))))))))
