(ns london-talk-2024.fizzbuzz2
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Tap-diffs [tap! xs]
  (let [>dxs (e/pure xs)
        >dxs (m/eduction (map tap!) >dxs)]
    (e/input >dxs))
  xs)

(e/defn RangeN [n]
  (e/diff-by identity (range 1 (inc n))))

(e/defn FizzBuzz [fizz buzz n]
  (cond
    (zero? (mod n (* 3 5))) (str fizz buzz)
    (zero? (mod n 3)) fizz
    (zero? (mod n 5)) buzz
    :else n))

#?(:cljs
   (do
     (def !fizz (atom 'fizz))
     (def !buzz (atom 'buzz))
     (def !n (atom 10))))

(e/defn FizzBuzzDemo []
  (let [ns ($ RangeN (e/watch !n))]
    (e/for [n ns]
      (let [x ($ FizzBuzz (e/watch !fizz) (e/watch !buzz) n)]
        (dom/div (dom/text x))))))

(comment
  ($ Tap-diffs #(println 'diff %))
  (reset! !fizz 'pop)
  (reset! !fizz 'fizz)
  (reset! !buzz 'buzz)
  (swap! !n inc)
  (reset! !n 100)
  (if (= 0 (int (mod ($ e/SystemTimeSecs) 2)))
    'fizz
    'pop))
