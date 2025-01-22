(ns docs-site.blog.waveform0
  (:require [clojure.math :as math]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-svg3 :as svg]
            [hyperfiddle.electric-scroll0 :refer [#?(:cljs resize-observer) IndexRing]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn A [i] (e/server (math/sin (/ i 7))))
(e/defn B [i] (e/server (math/cos (/ i 13))))

(e/defn Timer [] (ex/Throttle 1 (- (e/snapshot (e/System-time-ms)) (e/System-time-ms))))

(def scale 50)
(def bar-gap 15)
(def overscroll 8)
(def zoom 1)

(e/defn Bar [i v y-offset]
  (svg/rect (dom/props {:width (/ 10 zoom) :fill "#2ecc71" :opacity 0.8 :height (* (abs v) scale)
                        :x (* bar-gap i) :y (+ y-offset (if (pos? v) (- (* v scale)) 0))})))

(e/defn Track [F offset limit y-offset]
  (svg/g
    (e/for [i (IndexRing (+ limit overscroll) (- offset (/ overscroll 2)))]
      (Bar i (F i) y-offset))))

(e/defn Cursor [x]
  (svg/rect (dom/props {:fill "red" :width 1 :height "100%" :opacity 0.8 :x x})))

(e/defn Timeline [offset]
  (let [[height width] (e/input (resize-observer dom/node))
        limit-n (math/round (* (/ width bar-gap) zoom))
        locus-n (+ offset (/ limit-n 2))]
    (svg/svg (dom/props {:style {:border "1px solid gray"}
                         :height height :viewBox (str (* bar-gap offset) " 0 " width " " height)})
      (Track A offset limit-n 80)
      (Track B offset limit-n (+ 80 (* 2.5 scale)))
      (Cursor (* locus-n bar-gap)))
    locus-n))

(e/defn RecordDebug [m] (dom/pre (dom/text (-> m (update-vals #(.toPrecision % 3)) pr-str))))

(e/defn Waveform0 []
  (dom/props {:style {:height "300px"}})
  (let [offset-n (-> (Timer) (* 60) (/ 1000) math/round)
        locus (Timeline offset-n)]
    (RecordDebug {:a (A locus) :b (B locus)})))