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

(defn now-ms []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(letfn [(wait [gap t0]
          (m/sp
            (let [t (- (now-ms) t0)]
              (when (< t gap) (m/? (m/sleep (- gap t))))
              (now-ms))))]
  (e/defn Clock [hz]
    (let [hz (abs hz)]
      (e/When (not= 0 hz)
        (let [gap (/ 1000 hz)
              [spend t0] (tok/WithDataSlot (tok/CyclicToken (e/DOMVisible?) true?) (now-ms))]
          (e/When spend
            (spend (e/Task (wait gap t0)))))))))

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

(e/defn Wave []
  (dom/div
    (dom/props {:style {:display "flex", :flex-direction "column"}})
    (let [playing?  (PlayButton)
          hz        (HzRange)
          offset    (Tick playing? hz)
          zoom      (/ (ZoomPercentRange) 100)
          size      (* 40 zoom)
          bar-width (/ 10 zoom)
          bar-gap   (/ 15 zoom)]
      playing? hz zoom offset                       ; sample, order
      (svg/svg
        (dom/props {:style {:border "1px solid gray"}, :width 600 :height 600 :viewBox (str (* bar-gap offset) " 0 600 600")})
        (svg/g
          (dom/props {:transform "translate(0, 150)"})
          (e/for [i (scroll/IndexRing size offset)]
            (let [{:keys [sin cos]} (e/server {:sin (math/sin (/ i 13)) :cos (math/cos (/ i 7))})]
              (svg/rect
                (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :x (* bar-gap i)
                            :height (* (abs cos) 100)
                            :y (when (pos? cos) (- (* cos 100)))}))
              (svg/rect
                (dom/props {:width bar-width :fill "#2ecc71" :opacity 0.8 :x (* bar-gap i)
                            :height (* (abs sin) 100)
                            :y (+ 300 (if (pos? sin) (- (* sin 100)) 0))})))))))))
