(ns peternagy.seq-from-pull-test
  (:require [peternagy.seq-from-pull :as sfp]
            [contrib.str]
            [clojure.test :as t]))

(def data-map {:coll [{:foo 1, :bar 10}, {:foo 2, :bar 20}]
               "scalar" 42
               :map {:a 1 :b 2 :coll [1 2] :map {:x 1 :y 2}}})

(t/deftest noop
  (t/is (= []
          (sfp/seq-from-pull data-map []))))

(t/deftest selecting-keys
  (t/is (= [[["scalar"] 42 false]]
          (sfp/seq-from-pull data-map ["scalar"])))

  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (sfp/seq-from-pull data-map [:coll])))

  (t/is (= [[["scalar"] 42 false]
            [[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (sfp/seq-from-pull data-map ["scalar" :coll]))))

(t/deftest traversing-map
  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :b] 2 false]]
          (sfp/seq-from-pull data-map [{:map [:a :b]}])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]
            [[:map :map :y] 2 false]]
          (sfp/seq-from-pull data-map [{:map [{:map [:x :y]}]}]))))

(t/deftest traversing-coll
  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (sfp/seq-from-pull data-map [{:coll [:foo]}]))))

(t/deftest selecting-*
  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]
            [["scalar"] 42 false]
            [[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} false]]
          (sfp/seq-from-pull data-map ['*])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :b] 2 false]
            [[:map :coll] [1 2] false]
            [[:map :map] {:x 1, :y 2} false]]
          (sfp/seq-from-pull data-map [{:map ['*]}])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]
            [[:map :map :y] 2 false]]
          (sfp/seq-from-pull data-map [{:map [{:map ['*]}]}])))

  (t/testing "doesn't traverse collections"
    (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
            (sfp/seq-from-pull data-map [{:coll ['*]}])))))

(t/deftest searching
  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :map] {:x 1, :y 2} false]]
          (sfp/seq-from-pull data-map [{:map ['*]}] (fn [k v] (contrib.str/any-matches? [k v] "a")))))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]]
          (sfp/seq-from-pull data-map [{:map [{:map ['*]}]}] (fn [k v] (contrib.str/any-matches? [k v] "x")))))

  (t/testing "parents omitted if no children match"
    (t/is (= [] (sfp/seq-from-pull data-map [{:map ['*]}] (fn [_k _v] false))))
    (t/is (= [] (sfp/seq-from-pull data-map [{:map [{:map ['*]}]}] (fn [_k _v] false))))))
