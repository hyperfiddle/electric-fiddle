(ns dustingetz.painter
  "video: https://gist.github.com/dustingetz/d58a6134be310e05307ca0b586c30947
upstream: https://github.com/formicagreen/electric-clojure-painter"
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Painter []
  (e/client
    (dom/props {:style {:margin "0" :overflow "hidden" :background "lightblue" :user-select "none" :font-size "30px"}})
    (dom/element "style" (dom/text "@keyframes fadeout { from { opacity: 1; } to { opacity: 0; } }"))
    (dom/div
      (dom/props {:style {:width "100vw" :height "100vh"}})
      (let [!current-emoji (atom "🐱")
            mouse-down? (dom/MouseDown?)
            dones (e/cursor [[[x y e] done!] (dom/OnAll "mousemove"
                                               (fn [e]
                                                 (when mouse-down?
                                                   [(.-clientX e) (.-clientY e) @!current-emoji])))]
                    #_(println x y e)
                    (dom/div (dom/text e)
                      (dom/props {:style {:width "10px" :height "10px"
                                          :position "absolute"
                                          :left (str x "px")
                                          :top (str y "px")
                                          :user-select "none"
                                          :pointer-events "none"}}))
                    done!)]
        dones
        (dom/div
          (dom/props {:style {:background "#fff5" :backdrop-filter "blur(10px)" :position "fixed"
                              :top "0" :left "0" :height "100vh" :padding "10px"}})
          (e/for [emoji (e/amb "🕉" "🧬" "🧿" "🌀" "♻️" "🐍" "🐱" "🫥" "🌰" "🐞" "🐹" "🪙" "🕸" "📞")]
            (dom/div (dom/text emoji)
              (dom/props {:style {:cursor "pointer"}})
              (reset! !current-emoji (dom/On "click" (constantly emoji)))))
          (dom/div (dom/text "🗑️")
            (dom/props {:style {:cursor "pointer" :padding-top "50px"}})
            (when-some [t (e/Token (dom/On "click"))]
              (t (dones)))))))))