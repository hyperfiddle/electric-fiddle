(ns peternagy.pull-walker-test
  (:require [peternagy.pull-walker :as pw]
            [contrib.str]
            [clojure.test :as t]))

(def data-map {:coll [{:foo 1, :bar 10}, {:foo 2, :bar 20}]
               "scalar" 42
               :map {:a 1 :b 2 :coll [1 2] :map {:x 1 :y 2}}})

(t/deftest noop
  (t/is (= []
          (pw/walker data-map []))))

(t/deftest selecting-keys
  (t/is (= [[["scalar"] 42 false]]
          (pw/walker data-map ["scalar"])))

  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (pw/walker data-map [:coll])))

  (t/is (= [[["scalar"] 42 false]
            [[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (pw/walker data-map ["scalar" :coll]))))

(t/deftest traversing-map
  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :b] 2 false]]
          (pw/walker data-map [{:map [:a :b]}])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]
            [[:map :map :y] 2 false]]
          (pw/walker data-map [{:map [{:map [:x :y]}]}]))))

(t/deftest traversing-coll
  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
          (pw/walker data-map [{:coll [:foo]}]))))

(t/deftest selecting-*
  (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]
            [["scalar"] 42 false]
            [[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} false]]
          (pw/walker data-map ['*])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :b] 2 false]
            [[:map :coll] [1 2] false]
            [[:map :map] {:x 1, :y 2} false]]
          (pw/walker data-map [{:map ['*]}])))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]
            [[:map :map :y] 2 false]]
          (pw/walker data-map [{:map [{:map ['*]}]}])))

  (t/testing "doesn't traverse collections"
    (t/is (= [[[:coll] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]
            (pw/walker data-map [{:coll ['*]}])))))

(t/deftest searching
  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :a] 1 false]
            [[:map :map] {:x 1, :y 2} false]]
          (pw/walker data-map [{:map ['*]}] (fn [k v] (contrib.str/any-matches? [k v] "a")))))

  (t/is (= [[[:map] {:a 1, :b 2, :coll [1 2], :map {:x 1, :y 2}} true]
            [[:map :map] {:x 1, :y 2} true]
            [[:map :map :x] 1 false]]
          (pw/walker data-map [{:map [{:map ['*]}]}] (fn [k v] (contrib.str/any-matches? [k v] "x")))))

  (t/testing "parents omitted if no children match"
    (t/is (= [] (pw/walker data-map [{:map ['*]}] (fn [_k _v] false))))
    (t/is (= [] (pw/walker data-map [{:map [{:map ['*]}]}] (fn [_k _v] false))))))
