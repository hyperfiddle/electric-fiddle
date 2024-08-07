(ns dustingetz.painter
  "video: https://gist.github.com/dustingetz/d58a6134be310e05307ca0b586c30947
upstream: https://github.com/formicagreen/electric-clojure-painter"
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(def emojis ["🕉" "🧬" "🧿" "🌀" "♻️" "🐍" "🐱" "🫥" "🌰" "🐞" "🐹" "🪙" "🕸" "📞"])
#?(:cljs (def !mousedown (atom false)))
#?(:cljs (defonce !current-emoji (atom "🐱")))

(e/defn Painter []
  (e/client
    (dom/props {:style {:margin "0" :overflow "hidden" :background "lightblue" :user-select "none" :font-size "30px"}})
    (dom/element "style" (dom/text "@keyframes fadeout { from { opacity: 1; } to { opacity: 0; } }"))
    (dom/div
      (dom/props {:style {:width "100vw" :height "100vh"}})
      (when ($ dom/On "mousedown") (reset! !mousedown true))
      (when ($ dom/On "mouseup") (reset! !mousedown false))

      (let [vertices (e/cursor [[e clear!] ($ dom/OnAll "mousemove")]
                       (if @!mousedown
                         [(.-clientX e) (.-clientY e) @!current-emoji clear!] ; leave uncleared
                         (do (clear!) (e/amb))))]

        (e/cursor [[x y e _] vertices]
          #_(println x y e)
          (dom/div (dom/text e)
            (dom/props {:style {:width "10px" :height "10px"
                                :position "absolute"
                                :left (str x "px")
                                :top (str y "px")
                                :user-select "none"
                                :pointer-events "none"}})))

        (dom/div
          (dom/props {:style {:background "#fff5" :backdrop-filter "blur(10px)" :position "fixed"
                              :top "0" :left "0" :height "100vh" :padding "10px"}})
          (e/for-by identity [emoji emojis]
            (dom/div (dom/text emoji)
              (dom/props {:style {:cursor "pointer"}})
              (when ($ dom/On "click") (reset! !current-emoji emoji))))
          (dom/div (dom/text "🗑️")
            (dom/props {:style {:cursor "pointer" :padding-top "50px"}})
            (when ($ dom/On "click")
              ((->> (e/as-vec vertices) (map #(nth % 3)) (reduce comp))))))))))