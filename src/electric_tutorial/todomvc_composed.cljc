(ns electric-tutorial.todomvc-composed
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.todomvc :refer
             [Service TodoMVC-UI Effects #?(:cljs !state) #?(:clj !conn)]]))

(e/defn PopoverCascaded [i F]
  (dom/div (dom/props {:style {:position "absolute"
                               :left (str (* i 40) "px")
                               :top (str (-> i (* 40)) "px")}})
    (dom/props {:style {:z-index (+ i (if (dom/Mouse-over?) 1000 0))}})
    (F)))

(e/defn TodoMVC-composed []
  (e/client
    (dom/link (dom/props {:rel :stylesheet, :href "/todomvc.css"}))
    (dom/props {:class "todomvc" :style {:position "relative"}})
    (let [!conn (e/server (identity !conn)) ; todo sited vars
          !state (e/client (identity !state)) ; todo sited vars
          state (e/watch !state)
          db (e/server (e/watch !conn))]

      (Service
        (e/Partial Effects !conn db !state state)
        (e/for [i (e/amb 1 2 3)]
          (PopoverCascaded i
            (e/Partial TodoMVC-UI db state)))))))