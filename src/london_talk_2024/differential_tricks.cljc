(ns london-talk-2024.differential-tricks
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

#?(:clj (def !q (atom false)))

#?(:clj (def !xs (atom [:a :b :c])))
#?(:clj (def !ys (atom [1 2])))

(e/defn DifferentialTricks []
  (dom/h1 (dom/text "tricks"))
  (e/server

    (println 'result
      ($ Tap-diffs println
        (if (e/watch !q)
          42
          (e/amb))))))

(comment

  "amb (multiplex)"
  (inc (e/amb 0 1 2))

  "vector product"
  (vector (e/amb 1 2) (e/amb \a \b \c))

  "reactive product"
  #?(:clj (def !xs (atom [:a :b :c])))
  #?(:clj (def !ys (atom [1 2])))
  (let [xs (e/diff-by identity (e/watch !xs))
        ys (e/diff-by identity (e/watch !ys))])

  (println (vector xs ys)) :=
  ; [:a 0] [:a 1] [:a 2] [:b 0] [:b 1] [:b 2] [:c 0] [:c 1] [:c 2]

  (swap! !ys conj 3)
  ; [:a 3] [:b 3] [:c 3] -- just the changeset!

  "applicative product"
  (println (e/as-vec ...)) ; ordered
  ((e/amb + -) (e/amb 0 1 2) (e/amb 10 20))

  "unmount diff"
  #?(:clj (def !q (atom false)))
  ($ Tap-diffs println)
  (if (e/watch !q)
    42
    (e/amb))
  (reset! !q false)

  "optimistic updates")