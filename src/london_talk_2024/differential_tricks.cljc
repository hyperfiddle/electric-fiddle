(ns london-talk-2024.differential-tricks
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

#?(:clj (def !q (atom true)))
#?(:clj (def !xs (atom [0 1 2])))
#?(:clj (def !ys (atom [10 20])))
(comment
  (swap! !xs conj 3)
  (swap! !xs pop)
  (swap! !ys conj 30) ; just the changeset! What's missing?
  (swap! !ys conj 40)
  (swap! !ys pop)
  (swap! !q not))

(e/defn DifferentialTricks []
  (dom/h1 (dom/text "What's the applicative functor?"))

  (let [xs (e/server (e/diff-by identity (e/watch !xs)))
        ys (e/amb 0 1 2)]
    (println xs)
    (println ys)

    #_(e/for [x xs]
        (println x)))

  )


(comment
  (e/amb 0 1 2)
  inc
  vector (e/amb 10 20)
  (e/amb + -)
  e/as-vec
  (let [xs (e/diff-by identity (e/watch !xs))
        ys (e/diff-by identity (e/watch !ys))]
    (vector xs ys))
  (if (e/watch !q)
    42
    (e/amb))

  )