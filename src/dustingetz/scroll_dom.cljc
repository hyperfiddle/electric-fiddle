(ns dustingetz.scroll-dom
  (:require [contrib.data :refer [unqualify]]
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            #_[hyperfiddle.electric-ui4 :as ui]
            #?(:cljs goog.object)
            [missionary.core :as m]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

(defn throttle [dur >in]
  (m/ap
    (let [x (m/?> (m/relieve {} >in))]
      (m/amb x (do (m/? (m/sleep dur)) (m/amb))))))

#?(:cljs (defn sample-scroll-state! [scrollable]
           [(.. scrollable -scrollTop) ; optimization - detect changes (pointless)
            (.. scrollable -scrollHeight) ; snapshot height to detect layout shifts in flipped mode
            (.. scrollable -clientHeight)])) ; measured viewport height (scrollbar length)

#?(:cljs (defn scroll-state> [scrollable]
           (m/observe
             (fn [!]
               (let [sample (fn [] (! (sample-scroll-state! scrollable)))]
                 (.addEventListener scrollable "scroll" sample #js {"passive" true})
                 #(.removeEventListener scrollable "scroll" sample))))))

#?(:cljs (def !scrollStateDebug (atom nil)))

#?(:cljs (defn scroll-state< [scrollable]
           (->> (scroll-state> scrollable)
             (throttle 16) ; RAF interval
             (m/reductions {} [0 0 0])
             (m/relieve {})
             (m/latest (fn [[scrollTop scrollHeight clientHeight :as s]]
                         (reset! !scrollStateDebug {::scrollTop scrollTop
                                                    ::scrollHeight scrollHeight
                                                    ::clientHeight clientHeight})
                         s)))))

(defn window [xs offset limit]
  (subvec (vec xs) ; fast cast
    (Math/max offset 0)
    (Math/min (+ offset limit) (count xs))))

(def record-count 500)
(def page-size 100)

(e/defn DemoFixedHeightCounted
  "Scrolls like google sheets. this can efficiently jump through a large indexed collection"
  [record-count page-size xs!]
  (e/client
    (let [row-height 22] ; todo use relative measurement (browser zoom impacts px height)
      (dom/div (dom/props {:class "viewport" :style {:overflowX "hidden" :overflowY "auto"}})
        (let [[scrollTop] (e/input (scroll-state< dom/node))
              max-height (* record-count row-height)
              clamped-scroll-top (js/Math.min scrollTop max-height)
              offset (js/Math.floor (/ clamped-scroll-top row-height))]
          (dom/div (dom/props {:style {:height (str (* row-height record-count) "px") ; optional absolute scrollbar
                                       :padding-top (str clamped-scroll-top "px") ; seen elements are replaced with padding
                                       :padding-bottom (str (- max-height clamped-scroll-top) "px")}})

            (e/cursor [x (e/server (e/diff-by identity (window xs! offset page-size)))]
              (dom/div (dom/text x)))))))))

(defn abc
  ([N] (abc N 0))
  ([N start] (vec (for [j (range N)]
                    (char (-> (+ start j) #_(mod 26) (+ 97)))))))

(e/defn ScrollDemo []
  (e/client
    ; Requires css {box-sizing: border-box;}
    (dom/element "style" (dom/text ".header { position: fixed; z-index:1; top: 0; left: 0; right: 0; height: 100px; background-color: #abcdef; }"
                           ".footer { position: fixed; bottom: 0; left: 0; right: 0; height: 100px; background-color: #abcdef; }"
                           ".viewport { position: fixed; top: 100px; bottom: 100px; left: 0; right: 0; background-color: #F63; overflow: auto; }"))
    (dom/div (dom/props {:class "header"})
      (dom/dl
        (dom/dt (dom/text "scroll debug state"))
        (dom/dd (dom/pre (dom/text (pr-str (update-keys (e/watch !scrollStateDebug) unqualify)))))))
    ($ DemoFixedHeightCounted record-count page-size (e/server (vec (range record-count))))
    (dom/div (dom/props {:class "footer"})
      (dom/text "Try scrolling to the top, and resizing the window."))))
