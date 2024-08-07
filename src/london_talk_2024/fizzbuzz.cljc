(ns london-talk-2024.fizzbuzz
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :as rcf :refer [tests % tap with]]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

(e/defn RangeN [n]
  (e/diff-by identity (range 1 (inc n))))

(e/defn FizzBuzz [fizz buzz n]
  (e/cursor [i ($ RangeN n)]
    (cond
      (zero? (mod i (* 3 5))) (str fizz buzz)
      (zero? (mod i 3)) fizz
      (zero? (mod i 5)) buzz
      :else i)))

#?(:cljs
   (do
     (def !fizz (atom 'fizz))
     (def !buzz (atom 'buzz))
     (def !n (atom 10))))

(e/defn FizzBuzzDemo []
  (e/cursor [x ($ Tap-diffs println
                 (e/server ($ FizzBuzz
                             (e/client (e/watch !fizz))
                             (e/client (e/watch !buzz))
                             (e/client (e/watch !n)))))]
    (dom/div (dom/text x))))

(comment
  (reset! !fizz 'pop)
  (reset! !fizz 'fizz)
  (reset! !buzz 'buzz)
  (swap! !n inc))

#_(tests
    (def !fizz (atom 'fizz))
    (def !buzz (atom 'buzz))
    (def !n (atom 10))

    (with ((l/local {}
             (e/client
               (-> (e/$ FizzBuzz (e/watch !fizz) (e/watch !buzz) (e/watch !n))
                 e/as-vec tap)))
           tap tap)
      % := '[1 2 fizz 4 buzz fizz 7 8 fizz buzz]
      (reset! !buzz 'baz)
      % := '[1 2 fizz 4 baz fizz 7 8 fizz baz]
      (swap! !n inc)
      % := '[1 2 fizz 4 baz fizz 7 8 fizz baz 11]))

