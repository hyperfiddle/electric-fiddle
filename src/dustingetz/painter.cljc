(ns dustingetz.painter
  "video: https://gist.github.com/dustingetz/d58a6134be310e05307ca0b586c30947
upstream: https://github.com/formicagreen/electric-clojure-painter"
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(declare css)
(e/defn Painter []
  (e/client
    (dom/props {:class "painter"}) (dom/style (dom/text css))
    (dom/div (dom/props {:class "canvas"})
      (let [!current-emoji (atom "🐱")
            mouse-down? (dom/Mouse-down?)
            ts (e/for [[t [x y e]] (dom/On-all "mousemove"
                                     (fn [e]
                                       (when mouse-down?
                                         [(.-clientX e) (.-clientY e) @!current-emoji])))]
                 #_(println x y e)
                 (dom/div (dom/text e) (dom/props {:class "pixel"
                                                   :style {:left (str x "px")
                                                           :top (str y "px")}}))
                 t)
            clear-all! (let [ts (e/as-vec ts)]
                         (fn [] #_(doseq [t ts] (t)) (mapv (fn [f] (f)) ts) nil))]
        clear-all!
        (dom/div (dom/props {:class "sidebar"})
          (e/for [emoji (e/amb "🕉" "🧬" "🧿" "🌀" "♻️" "🐍" "🐱" "🫥" "🌰" "🐞" "🐹" "🪙" "🕸" "📞")]
            (dom/div (dom/text emoji) (dom/props {:style {:cursor "pointer"}})
              (dom/On "click" (fn [e] (.stopPropagation e) (reset! !current-emoji emoji) nil) @!current-emoji)))
          (dom/div (dom/props {:class "trash"}) (dom/text "🗑️")
            (let [[t err] (e/Token (dom/On "click" (fn [e] (.stopPropagation e) e) nil))]
              (when t (case (clear-all!) (t))))) ; todo slow for 200+ items
          (dom/div (dom/text (e/Count ts))))))))

(def css "
.painter { margin:0; overflow:hidden; background:lightblue; user-select:none; font-size: 30px;}
.painter .canvas { width:100vw; height: 100vh;}
.painter .pixel { width: 10px; height: 10px; position: absolute; user-select: none; pointer-events: none;}
.painter .sidebar { background:#fff5; backdrop-filter: blur(10px); position: fixed; top: 0; left:0; height:100vh; padding:10px;}
.painter .sidebar .trash {cursor:pointer; margin-top:50px;}
@keyframes fadeout { from { opacity: 1; } to { opacity: 0; } }")