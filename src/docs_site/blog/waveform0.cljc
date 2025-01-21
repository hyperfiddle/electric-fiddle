(ns docs-site.blog.waveform0
  (:require [clojure.math :as math]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.electric-scroll0 :as scroll]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Clock [hz]
  (let [hz (abs hz)]
    (e/When (not= 0 hz)
      (math/round (-> (e/System-time-ms) (* hz) (/ 1000))))))

(e/defn PlayButton []
  (dom/button
    (let [playing? (dom/On "click" (partial (fn [!b e] (swap! !b not)) (atom false)) false)]
      (dom/text (if playing? "pause" "play"))
      playing?)))

(e/defn HzRange [v]
  (let [hz (dom/input (dom/props {:id "hz" :type "range", :min -60, :max 60, :value v, :style {:width "300px"}})
             (dom/On "input" #(-> % .-target .-value parse-long) v))]
    (dom/label (dom/props {:for "hz"}) (dom/text (str hz " Hz")))
    hz))

(e/defn Tick [playing? hz]
  (let [!offset (atom 0)]
    (when playing?
      ((fn [_ hz] (cond (pos? hz) (swap! !offset inc)
                        (zero? hz) nil
                        :else (swap! !offset dec)))
       (Clock hz) hz))
    (e/watch !offset)))

(e/defn Cursor [viewbox-x bar-gap]
  (let [mouse-x (+ viewbox-x (dom/On "mousemove" #(- (.-clientX %) (-> (.-currentTarget %) .getBoundingClientRect .-left)) 0))]
    (svg/rect (dom/props {:width 1, :height "100%" :fill "red", :opacity 0.8, :x mouse-x}))
    (math/floor (/ (ex/Throttle 16 mouse-x) bar-gap))))

(e/defn RecordViewer [{:keys [a b]}]
  (dom/dl
    (dom/dt (dom/text "a")) (dom/dd (dom/text (some-> a (.toPrecision 2))))
    (dom/dt (dom/text "b")) (dom/dd (dom/text (some-> b (.toPrecision 2))))))

(def scale 50)
(def z-factor 15)
(def h-factor 300)
(def overscroll 8)

(declare css)
(e/defn Waveform0 []
  (dom/style (dom/text css))
  (dom/div (dom/props {:style {:display "flex", :flex-direction "column"}})
    (let [playing?  (dom/div (PlayButton))
          hz        (HzRange 60)
          offset    (Tick playing? hz)
          zoom      1
          [_height width] (e/input (scroll/resize-observer dom/node))
          size      (math/round (* (/ width z-factor) zoom))
          bar-width (/ 10 zoom)
          bar-gap   (/ z-factor zoom)
          viewbox-x (* bar-gap offset)
          !record (atom nil), record (e/watch !record)]
      playing? hz zoom offset           ; sample, order
      (svg/svg
        (let [selected (Cursor viewbox-x bar-gap)]
          (dom/props {:style {:border "1px solid gray"}, :height h-factor :viewBox (str viewbox-x " 0 " width " " h-factor)})
          (svg/g (dom/props {:transform "translate(0, 100)"})
            (e/for [i (scroll/IndexRing (+ size overscroll) (- offset (/ overscroll 2)))]
              (let [{:keys [a b] :as v} (e/server {:a (math/sin (/ i 13))
                                                   :b (math/cos (/ i 7))})]
                (when (= i selected) (reset! !record v))
                (svg/rect (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :height (* (abs a) scale)
                                      :x (* bar-gap i) :y (when (pos? a) (- (* a scale)))}))
                (svg/rect (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :height (* (abs b) scale)
                                      :x (* bar-gap i) :y (+ (* 2.5 scale) (if (pos? b) (- (* b scale)) 0))})))))))
      (RecordViewer record))))

(def css "
dl { margin: 0; display: grid; grid-template-columns: max-content auto; }
dt { grid-column: 1; }
dd { grid-column: 2; margin-left: 1em; margin-bottom: .5em; }")