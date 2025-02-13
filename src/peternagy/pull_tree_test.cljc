(ns peternagy.pull-tree-test
  (:require [peternagy.pull-tree :as pt]
            [clojure.test :as t]
            [clojure.java.io :as io]))

(def data-map {:a [{:foo 1, :bar 10}, {:foo 2, :bar 20}]
               "b" 42})

(t/deftest noop
  (t/is (= [] (pt/pull data-map [])))
  (t/is (= [] (pt/pull data-map nil))))

(t/deftest key-select
  (t/is (= [
            [[:a]                                   ; get-in
             [{:foo 1, :bar 10}, {:foo 2, :bar 20}] ; value
             false]                                 ; branch?
            ]
          (pt/pull data-map [:a])))

  (t/is (= [[["b"] 42 false]]
          (pt/pull data-map ["b"]))))

(t/deftest keys-of-coll
  (t/is (= [[[0] {:a 1} false]
            [[1] {:a 2} false]]
          (pt/pull [{:a 1} {:a 2}] ['*]))))

(t/deftest star-on-map
  (t/is (= [[[:a] 1 false]
            [[:b] 2 false]]
          (pt/pull {:a 1 :b 2} ['*]))))

(t/deftest unroll-once
  (t/is (= [[[:a] [{:foo 1, :bar 10}, {:foo 2, :bar 20}] true]
            [[:a 0] {:foo 1, :bar 10} false]
            [[:a 1] {:foo 2, :bar 20} false]]
          (pt/pull data-map {:a ['*]}))))

(t/deftest unroll-twice
  (t/is (= [[[:a] [{:foo 1, :bar 10}, {:foo 2, :bar 20}] true]
            [[:a 0] {:foo 1, :bar 10} true]
            [[:a 0 :foo] 1 false]
            [[:a 0 :bar] 10 false]
            [[:a 1] {:foo 2, :bar 20} true]
            [[:a 1 :foo] 2 false]
            [[:a 1 :bar] 20 false]]
          (pt/pull data-map [{:a {'* ['*]}}]))))

(t/deftest dumb-eval
  (t/is (= [[[`(identity ~'%)] data-map false]]
          (pt/pull data-map `(identity ~'%))))

  (t/is (= [[["b"] 42 true]
            [["b" `(inc ~'%)] 43 false]]
          (pt/pull data-map {"b" `(inc ~'%)}))))

(comment
  (vec (pt/pull data-map :a))
  [[[:a] [{:foo 1, :bar 10} {:foo 2, :bar 20}] false]]

  (vec (pt/pull data-map {:a ['*]}))
  [[[:a] [{:foo 1, :bar 10} {:foo 2, :bar 20}] true]
   [[:a 0] {:foo 1, :bar 10} false]
   [[:a 1] {:foo 2, :bar 20} false]]

  (vec (pt/pull data-map {:a {'* ['*]}}))
  [[[:a] [{:foo 1, :bar 10} {:foo 2, :bar 20}] true]
   [[:a 0] {:foo 1, :bar 10} true]
   [[:a 0 :foo] 1 false]
   [[:a 0 :bar] 10 false]
   [[:a 1] {:foo 2, :bar 20} true]
   [[:a 1 :foo] 2 false]
   [[:a 1 :bar] 20 false]]
  )

(defn file-name [^java.io.File f] (.getName f))
(defn file-children [^java.io.File f] (.listFiles f))

(t/deftest pull-files-limited-recursion
  (t/is (= 1 1))
  (vec (pt/pull (io/file "./resources")
         [`(file-name ~'%)
          {`(file-children ~'%)
           {'* [`(file-name ~'%)
                 {`(file-children ~'%)
                  {'* [`(file-name ~'%)
                        {`(file-children ~'%)
                         {'* [`(file-name ~'%)]}}]}}]}}])))

(comment
  {:a [{:foo 1, :bar 1}, {:foo 2, :bar 2}]}

  ;; :a [{:foo 1, :bar 1} {:foo 2, :bar 2}]

  ;; :a
  ;;   0 {:foo 1, :bar 1}
  ;;   1 {:foo 2, :bar 2}
  ;;   2 {:foo 3, :bar 3}

  ;; :a
  ;;   0
  ;;     :foo 1
  ;;     :bar 1
  ;;   1
  ;;     :foo 2
  ;;     :bar 2
  ;;   2
  ;;     :foo 3
  ;;     :bar 3

  )

#_(t/deftest ir
  (t/is (= [[:descend :a] [:descend-all] [:read-all] [:ascend] [:ascend]]
          (pt/->ir [{:a {'* ['*]}}]))))
