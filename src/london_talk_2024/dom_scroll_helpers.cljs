(ns london-talk-2024.dom-scroll-helpers
  (:require [missionary.core :as m]))

(defn throttle [dur >in]
  (m/ap
    (let [x (m/?> (m/relieve {} >in))]
      (m/amb x (do (m/? (m/sleep dur)) (m/amb))))))

(defn scroll-state [scrollable]
  (->> (m/observe
         (fn [!]
           (! [0 0 0])
           (let [sample (fn [] (! [(.. scrollable -scrollTop) ; optimization - detect changes (pointless)
                                   (.. scrollable -scrollHeight) ; snapshot height to detect layout shifts in flipped mode
                                   (.. scrollable -clientHeight)]))] ; measured viewport height (scrollbar length)
             (.addEventListener scrollable "scroll" sample #js {"passive" true})
             #(.removeEventListener scrollable "scroll" sample))))
    (throttle 16) ; RAF interval
    (m/relieve {})))

(defn resize-observer [node]
  (m/relieve {}
    (m/observe (fn [!] (! [(.-clientHeight node)
                           (.-clientWidth node)])
                 (let [obs (new js/ResizeObserver
                             (fn [entries]
                               (let [content-box-size (-> entries (aget 0) .-contentBoxSize (aget 0))]
                                 (! [(.-blockSize content-box-size)
                                     (.-inlineSize content-box-size)]))))]
                   (.observe obs node) #(.unobserve obs))))))