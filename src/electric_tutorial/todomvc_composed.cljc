(ns electric-tutorial.todomvc-composed
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.todomvc :as todomvc :refer
             [Service TodoMVC-UI Effects !state state state0
              db Transact! Slow-transact #?(:clj !conn)]]))

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
    (binding [!state (atom state0)]
      (binding [state (e/watch !state)]
        (binding [db (e/server (e/watch !conn))
                  Transact! (e/server (e/Partial Slow-transact (e/client (::todomvc/delay state)) !conn))]
          (Service (Effects)
            (e/for [i (e/amb 1 2 3)]
              (PopoverCascaded i
                (e/Partial TodoMVC-UI db state)))))))))