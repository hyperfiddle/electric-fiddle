(ns dustingetz.fizzbuzz2
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

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

(e/defn FizzBuzz2Demo []
  (let [ns (RangeN (e/watch !n))
        xs (e/for [n ns]
             (FizzBuzz (e/watch !fizz) (e/watch !buzz) n))]
    (Tap-diffs xs)
    (e/for [x xs]
      (dom/div (dom/text x)))))

(comment

  (reset! !fizz 'pop)
  (reset! !fizz 'fizz)
  (reset! !buzz 'buzz)
  (swap! !n inc)
  (reset! !n 100)
  (if (= 0 (int (mod (e/SystemTimeSecs) 2)))
    'fizz
    'pop))
