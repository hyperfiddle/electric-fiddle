(ns dustingetz.logic
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-local-def3 :as l]))

(defn has-blue-house [house] (= house "blue"))
#_(defn tea-drinker-doesnt-have-a-dog [drink pet] (and (= drink "tea") (= pet "dog")))
(letfn [(tea-not-dog [d p] (and (= d "tea") (not= p "dog")))]
  (defn tea-drinker-doesnt-have-a-dog [d1 d2 d3 p1 p2 p3]
    (or (tea-not-dog d1 p1) (tea-not-dog d2 p2) (tea-not-dog d3 p3))))
(letfn [(green-coffee [h d] (and (= h "green") (= d "coffee")))]
  (defn green-houser-drinks-coffee [h1 h2 h3 d1 d2 d3]
    (or (green-coffee h1 d1) (green-coffee h2 d2) (green-coffee h3 d3))))
(defn doesnt-have-cat [pet] (not= "cat" pet))
(defn has-bird [pet] (= "bird" pet))
(letfn [(milk-red [d h] (and (= d "milk") (= h "red")))]
  (defn milk-drinker-lives-in-red-house [d1 d2 d3 h1 h2 h3]
    (or (milk-red d1 h1) (milk-red d2 h2) (milk-red d3 h3))))
(letfn [(red-bird [h p] (and (= h "red") (= p "bird")))]
  (defn red-houser-has-a-bird [h1 h2 h3 p1 p2 p3]
    (or (red-bird h1 p1) (red-bird h2 p2) (red-bird h3 p3))))

(e/defn Disj [v* x] (e/for [v v*] (if (= x v) (e/amb) v)))

(defmacro When [x & body] `(if ~x (do ~@body) (e/amb)))

(e/defn Logic* []
  (let [houses (e/amb "blue" "green" "red")
        pets (e/amb "dog" "cat" "bird")
        drinks (e/amb "tea" "coffee" "milk")]
    (e/for [john-house houses]
      (e/for [sarah-house (-> houses (Disj john-house))]
        (e/for [mike-house (-> houses (Disj john-house) (Disj sarah-house))]
          (e/for [john-pet pets]
            (e/for [sarah-pet (-> pets (Disj john-pet))]
              (e/for [mike-pet (-> pets (Disj john-pet) (Disj sarah-pet))]
                (e/for [john-drink drinks]
                  (e/for [sarah-drink (-> drinks (Disj john-drink))]
                    (e/for [mike-drink (-> drinks (Disj john-drink) (Disj sarah-drink))]
                      (When (and
                              (has-blue-house john-house)
                              (tea-drinker-doesnt-have-a-dog
                                john-drink sarah-drink mike-drink
                                john-pet   sarah-pet   mike-pet)
                              (green-houser-drinks-coffee
                                john-house sarah-house mike-house
                                john-drink sarah-drink mike-drink)
                              (doesnt-have-cat sarah-pet)
                              (has-bird mike-pet)
                              (red-houser-has-a-bird
                                john-house sarah-house mike-house
                                john-pet sarah-pet mike-pet)
                              (milk-drinker-lives-in-red-house
                                john-drink sarah-drink mike-drink
                                john-house sarah-house mike-house))
                        {:john  [john-house john-pet john-drink]
                         :sarah [sarah-house sarah-pet sarah-drink]
                         :mike  [mike-house mike-pet mike-drink]}))))))))))))

(e/defn Logic []
  (dom/pre (dom/text (pr-str (Logic*)))))

(comment (time ((l/single {} (prn (Logic*))) prn prn)))
;; {:mike ["red" "bird" "milk"], :sarah ["green" "dog" "coffee"], :john ["blue" "cat" "tea"]}
;; "Elapsed time: 231.37984 msecs"


; prime-sum-pair from SICP

(defn prime? [n] ; from claude
  (cond
    (<= n 1) false
    (= n 2) true
    (even? n) false
    :else (not-any? #(zero? (rem n %))
            (range 3 (inc (Math/sqrt n)) 2))))

(comment (map-indexed vector (map prime? (range 20))))

(defn do_ [& xs] (last xs))

(defmacro Where [test? & body]
  `(if test?
     (do ~@body)
     (e/amb)))

(e/defn Prime-sum-pair [as bs]
  #_(e/for [as as, bs bs])
  (if (prime? (+ as bs))
    [as bs]
    (e/amb)))

(e/defn Demo-prime-sum-pair []
  (let [result (e/as-vec ; [[3 20] [3 110] [8 35]]
                 (Prime-sum-pair
                   (e/amb 1 3 5 8)
                   (e/amb 20 35 110)))]
    (dom/text (pr-str result))))