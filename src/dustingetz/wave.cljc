(ns dustingetz.wave
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-svg3 :as svg]
    [hyperfiddle.electric-scroll0 :as scroll]
    [hyperfiddle.electric-dom3 :as dom]
    [clojure.math :as math]
    [missionary.core :as m]))

(defn now-ms []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn hz> [hz clock]
  (let [gap (/ 1000 hz)]
    (m/ap (let [!prev-ms (atom 0)
                _ (m/?> clock)
                t (- (now-ms) @!prev-ms)]
            (when (< t gap) (m/? (m/sleep (- gap t))))
            (reset! !prev-ms (now-ms))))))

(e/defn Wave []
  (let [!offset (atom 0), offset (e/watch !offset)
        !playing? (atom false), playing? (e/watch !playing?)
        !viewbox-x (atom 0), viewbox-x (e/watch !viewbox-x)]
    (dom/div
      (dom/props {:style {:display "flex", :flex-direction "column"}})
      (dom/button
        (dom/text (if playing? "pause" "play"))
        (dom/props {:style {:width "8rem"}})
        (dom/On "click" (fn [_] (swap! !playing? not)) nil))
      (let [hz (dom/input
                 (dom/props {:id "hz" :type "range", :min 1, :max 60, :value 20, :style {:width "600px"}})
                 (dom/On "input" #(-> % .-target .-value parse-long) 20))]
        (dom/label (dom/props {:for "hz"}) (dom/text (str hz " Hz")))
        (when playing?                  ; reuse system clock which stops on window blur
          ((fn [_] (swap! !offset inc)) (e/input (hz> hz (e/pure (e/System-time-ms))))))
        (svg/svg
          (dom/props {:style {:border "1px solid gray"}, :width 600 :height 300 :viewBox (str viewbox-x " 0 600 300")})
          (svg/g
            (dom/props {:transform "translate(0, 150)"})
            (e/for [i (scroll/IndexRing 40 offset)]
              (let [[i v] (e/server [i (math/cos (/ i 3))])] ; synchronize `i` with `v` so the repaint is in sync
                (when (>= i 40)                              ; don't run on load
                  ((fn [_] (swap! !viewbox-x + 15)) v))      ; delay update until `v` arrives so paint is in sync
                (svg/rect
                  (dom/props {:width 10 :fill "#2ecc71" :opacity 0.8 :x (* 15 i)
                              :height (* (abs v) 100)
                              :y (when (pos? v) (- (* v 100)))}))))))))))
