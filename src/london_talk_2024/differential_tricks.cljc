(ns london-talk-2024.differential-tricks
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

#?(:clj (def !q (atom true)))
#?(:clj (def !xs (atom [0 1 2])))
#?(:clj (def !ys (atom [10 20])))
(comment
  (swap! !ys conj 30) ; gives us what was missing
  (swap! !ys conj 40)
  (swap! !q not))

(e/defn DifferentialTricks []
  (dom/h1 (dom/text "tricks"))

  (e/server
    (println
      ($ Tap-diffs println

        42

        ))))


(comment
  (e/amb 0 1 2)
  inc
  vector (e/amb 10 20)
  (e/amb + -)
  e/as-vec
  (let [xs (e/diff-by identity (e/watch !xs))
        ys (e/diff-by identity (e/watch !ys))]
    (vector xs ys)) ; just the changeset! What's missing?
  (if (e/watch !q)
    42
    (e/amb))

  )