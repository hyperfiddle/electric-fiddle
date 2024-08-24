(ns dustingetz.fizzbuzz
  (:require [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

(e/defn RangeN [n]
  (e/diff-by identity (range 1 (inc n))))

(e/defn FizzBuzz [fizz buzz ns]
  (e/for [n ns] ; materialize individual elements from collection diffs
    (cond
      (zero? (mod n (* 3 5))) (str fizz buzz)
      (zero? (mod n 3)) fizz
      (zero? (mod n 5)) buzz
      :else n)))

#?(:cljs
   (do
     (def !fizz (atom 'fizz))
     (def !buzz (atom 'buzz))
     (def !n (atom 10))))

(e/defn FizzBuzzDemo []
  (e/client
    (let [fizz (e/watch !fizz)
          buzz (e/watch !buzz)
          n (e/watch !n)
          ns (RangeN n)
          xs (FizzBuzz fizz buzz ns)]
      (Tap-diffs xs)
      (e/for [x xs]
        (dom/div (dom/text x))))))

(comment
  (reset! !fizz 'pop)
  (reset! !fizz 'fizz)
  (reset! !buzz 'buzz)
  (swap! !n inc)
  (reset! !n 100)
  (if (= 0 (int (mod (e/SystemTimeSecs) 2)))
    'fizz
    'pop))
