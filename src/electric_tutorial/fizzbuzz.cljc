(ns electric-tutorial.fizzbuzz
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Input*]]))

(defn clamp [n min max] (Math/min (Math/max n min) max))

(e/defn RangeN [n]
  (e/diff-by identity (range 1 (inc n)))) ; unsited expr (i.e., dynamically sited)

(declare css)
(e/defn FizzBuzz []
  (e/client
    (let [fizz (Input* "fizz")
          buzz (Input* "buzz")
          n (-> (Input* 10 :type "number" :min "0" :max "100")
              parse-long (clamp 0 100))
          ns (RangeN n) ; differential collection
          xs (e/for [n ns] ; materialize individual elements from collection diffs
               (cond
                 (zero? (mod n (* 3 5))) (str fizz buzz)
                 (zero? (mod n 3)) fizz
                 (zero? (mod n 5)) buzz
                 :else n))]
      fizz buzz ; force lazy let statements otherwise sampled conditionally!
      (e/Tap-diffs xs) ; see console
      (e/for [x xs]
        (dom/div (dom/text x))))))