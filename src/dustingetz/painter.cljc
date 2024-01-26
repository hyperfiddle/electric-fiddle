(ns dustingetz.painter
  "video: https://gist.github.com/dustingetz/d58a6134be310e05307ca0b586c30947
upstream: https://github.com/formicagreen/electric-clojure-painter"
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]))

(def emojis ["🕉" "🧬" "🧿" "🌀" "♻️" "🐍" "🐱" "🫥" "🌰" "🐞" "🐹" "🪙" "🕸" "📞"])
#?(:cljs (def mousedown (atom false)))
#?(:cljs (defonce current-emoji (atom "🐱")))
#?(:clj (defonce vertices (atom [])))

(e/defn Painter []
  (e/client
    (dom/style {:margin "0" :overflow "hidden" :background "lightblue" :user-select "none" :font-size "30px"})
    (dom/element "style" (dom/text "@keyframes fadeout { from { opacity: 1; } to { opacity: 0; } }"))
    (dom/div
      (dom/style {:width "100vw" :height "100vh"})
      (dom/on "mousedown" (e/fn [e] (reset! mousedown true)))
      (dom/on "mouseup" (e/fn [e] (reset! mousedown false)))

      (dom/on "mousemove"
        (e/fn [e] (let [x (.-clientX e) y (.-clientY e)
                        m (e/watch mousedown)
                        e (e/watch current-emoji)]
                    (when m
                      (e/server
                        (swap! vertices conj [x y e]))))))

      (dom/div
        (dom/style {:background "#fff5" :backdrop-filter "blur(10px)" :position "fixed"
                    :top "0" :left "0" :height "100vh" :padding "10px"})
        (e/for-by identity [emoji emojis]
          (dom/div (dom/text emoji)
            (dom/style {:cursor "pointer"})
            (dom/on "click" (e/fn [e] (reset! current-emoji emoji)))))
        (dom/div (dom/text "🗑️")
          (dom/style {:cursor "pointer" :padding-top "50px"})
          (dom/on "click" (e/fn [e] (e/server (reset! vertices []))))))

      (dom/div
        (e/for-by identity [[x y e] (e/server (e/watch vertices))]
          (dom/div (dom/text e)
            (dom/style {:width "10px" :height "10px"
                        :position "absolute"
                        :left (str x "px")
                        :top (str y "px")
                        :user-select "none" :z-index "-1" :pointer-events "none"})))))))