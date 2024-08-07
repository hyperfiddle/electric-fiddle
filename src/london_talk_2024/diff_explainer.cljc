(ns london-talk-2024.diff-explainer
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

#?(:clj (def !xs (atom [0 1 2])))

(e/defn DiffExplainer []
  (e/server
    (e/cursor [x (e/diff-by identity (e/watch !xs))]
      (println x)  ; what is the functor?
      )))

(comment
  (swap! !xs conj 3)
  (swap! !xs conj 4)
  (reset! !xs [1 2]))
