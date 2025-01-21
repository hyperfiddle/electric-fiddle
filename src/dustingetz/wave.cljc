(ns dustingetz.wave
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-svg3 :as svg]
    [hyperfiddle.electric-scroll0 :as scroll]
    [hyperfiddle.token-zoo0 :as tok]
    [hyperfiddle.electric-dom3 :as dom]
    [clojure.math :as math]
    [contrib.debug :as dbg]
    [contrib.data :refer [->box]]
    [missionary.core :as m]))

(e/defn Clock [hz]
  (let [hz (abs hz)]
    (e/When (not= 0 hz)
      (math/round (-> (e/System-time-ms) (* hz) (/ 1000))))))

(defn ->toggler []
  (let [<b> (->box false)]
    (fn [_] (-> (<b>) not (<b>)))))

(e/defn PlayButton []
  (dom/button
    (dom/props {:style {:width "8rem"}})
    (let [playing? (dom/On "click" (->toggler) false)]
      (dom/text (if playing? "pause" "play"))
      playing?)))

(e/defn HzRange []
  (let [hz (dom/input
             (dom/props {:id "hz" :type "range", :min -60, :max 60, :value 20, :style {:width "600px"}})
             (dom/On "input" #(-> % .-target .-value parse-long) 20))]
    (dom/label (dom/props {:for "hz"}) (dom/text (str hz " Hz")))
    hz))

(e/defn Tick [playing? hz]
  (let [!offset (atom 0)]
    (when playing?
      ((fn [_ hz] (cond (pos? hz) (swap! !offset inc)
                        (zero? hz) nil
                        :else (swap! !offset dec)))
       (Clock hz) hz))
    (e/watch !offset))
  ;; alternate token impl
  #_(let [[t offset] (tok/WithDataSlot (tok/TokenNofail (Clock hz)) 0)]
    (when (and playing? t)
      (t ((cond (pos? hz) inc (zero? hz) identity (neg? hz) dec) offset)))
    offset))

(e/defn ZoomPercentRange []
  (let [p (dom/input
             (dom/props {:id "p" :type "range", :min 10, :max 300, :value 100, :step 10, :style {:width "600px"}})
             (dom/On "input" #(-> % .-target .-value parse-long) 100))]
    (dom/label (dom/props {:for "p"}) (dom/text (str p "%")))
    p))

(e/defn Ruler [size offset step i->v-str bar-gap bar-width major?]
  (let [size (-> size (quot step) (* (inc step)))] ; size has to be multiple of step
    (svg/g
      (e/for [i (scroll/Ring size offset step)]
        (let [x (+ (* bar-gap i) (/ bar-width 2))] ; center ruler to bar's center
          (svg/rect
            (dom/props {:stroke "black", :opacity 0.8 :x x, :y 0, :height (if (major? i) 10 5), :width 1}))
          (when (major? i)
            (svg/text
              (dom/props {:x x, :y 25, :font-size "12px", :text-anchor "middle"})
              (dom/text (i->v-str i)))))))))

(e/defn Selector [viewbox-x bar-gap]
  (let [mouse-x (+ viewbox-x
                  (dom/On "mousemove" #(- (.-clientX %) (-> (.-currentTarget %) .getBoundingClientRect .-left)) 0))]
    (svg/rect
      (dom/props {:width 1, :height "100%" :fill "red", :opacity 0.8, :x mouse-x}))
    (math/floor (/ mouse-x bar-gap))))

(e/defn RecordViewer [record]
  (dom/pre (dom/text (pr-str record))))

(e/defn Wave []
  (dom/div
    (dom/props {:style {:display "flex", :flex-direction "column"}})
    (let [playing?  (PlayButton)
          hz        (HzRange)
          offset    (Tick playing? hz)
          zoom      (/ (ZoomPercentRange) 100)
          [_height width] (e/input (scroll/resize-observer dom/node))
          size      (math/round (* (/ width 15) zoom))
          bar-width (/ 10 zoom)
          bar-gap   (/ 15 zoom)
          viewbox-x (* bar-gap offset)
          !record (atom nil), record (e/watch !record)]
      playing? hz zoom offset           ; sample, order
      (svg/svg
        (let [selected (Selector viewbox-x bar-gap)]
          (dom/props {:style {:border "1px solid gray"}, :height 600 :viewBox (str viewbox-x " 0 " width " 600")})
          (Ruler size offset 2 identity bar-gap bar-width #(zero? (mod % 10)))
          (svg/g
            (dom/props {:transform "translate(0, 150)"})
            (e/for [i (scroll/IndexRing size offset)]
              (let [{:keys [sin cos] :as v} (e/server {:sin (math/sin (/ i 13)) :cos (math/cos (/ i 7))})]
                (when (= i selected) (reset! !record v))
                (svg/rect
                  (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :x (* bar-gap i)
                              :height (* (abs cos) 100)
                              :y (when (pos? cos) (- (* cos 100)))}))
                (svg/rect
                  (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :x (* bar-gap i)
                              :height (* (abs sin) 100)
                              :y (+ 300 (if (pos? sin) (- (* sin 100)) 0))})))))))
      (RecordViewer record))))
