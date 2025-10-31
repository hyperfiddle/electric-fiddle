(ns electric-tutorial.cons
  (:require
    [hyperfiddle.electric3 :as e]))

(e/defn Cons_ [a b]
  (e/fn [F] (F a b)))

(e/defn Car [C]
  (C (e/fn [a b] a)))

(e/defn Cdr [C]
  (C (e/fn [a b] b)))

(e/defn ConsDemo []
  (e/client
    (let [a (e/server (e/Offload #(do (Thread/sleep 1000) 1))) ; pending
          x (Cons_ a (e/client 2))] ; must not delay the 2 to wait for the 1
      (println (Cdr x)) ; extract 2 without waiting on 1
      (println (Car x)) ; only 1 is delayed
      )))
