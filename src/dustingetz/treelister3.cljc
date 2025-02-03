(ns dustingetz.treelister3 "
treeseq recursion scheme which supports ergonomic search, i.e. retaining intermediate path nodes
for any matching leaves"
  (:require contrib.str
            [hyperfiddle.rcf :refer [tests]]
            [contrib.debug :as dbg]
            [clojure.string :as str]))

(defn data-children [x]
  (cond (map? x) (seq x)
        (map-entry? x) (when-some [v* (data-children (val x))]
                         (let [k (key x)]
                           (mapv (fn [[p v]] [[k p] v]) v*)))
        (coll? x) (into [] (map-indexed vector) x)
        :else nil))

(tests
  (data-children {:a 1}) := [[:a 1]]
  (data-children (first {:a 1})) := nil
  (data-children (first {:a [:x :y]})) := [[[:a 0] :x] [[:a 1] :y]]
  (data-children [:a :b :c]) := [[0 :a] [1 :b] [2 :c]]
  (into #{} (map first) (data-children #{:a :b :c})) := #{0 1 2}) ; set order not guaranteed, bad

(defn -tree-list [path x children-fn keep?]
  (if-some [c* (seq (children-fn x))]
    (when-some [v* (seq (into [] (mapcat (fn [[p v]] (-tree-list (conj path p) v children-fn keep?))) c*))]
      (cons [path x true] v*))
    (when (keep? x) [[path x]])))

(defn treelist
  ([x] (treelist data-children x))
  ([children-fn x] (treelist children-fn (constantly true) x))
  ([children-fn keep? x]
   (-tree-list [] x children-fn keep?)))

(tests
  (treelist (fn [x] (map-indexed vector (:children x))) (fn [v] (or (:dir v) (-> v :file #{"a"})))
    {:dir "x" :children [{:file "a"} {:file "b"}]})
  :=
  '([[] {:dir "x", :children [{:file "a"} {:file "b"}]}]
    [[0] {:file "a"}])

  "directory is kept if there are no children matching keep?"
  (treelist (fn [x] (map-indexed vector (:children x))) (fn [v] (or (:dir v) (-> v :file #{"nope"})))
    {:dir "x" :children [{:file "a"} {:file "b"}]})
  (count (vec *1)) := 1)

(tests
  (treelist data-children {:foo {:bar ["baz" "quux"]}
                           :x   1})
  := [[[]             {:foo {:bar ["baz" "quux"]}, :x 1}]
      [[:foo]         {:bar ["baz" "quux"]}]
      [[:foo :bar]    ["baz" "quux"]]
      [[:foo :bar 0]  "baz"]
      [[:foo :bar 1]  "quux"]
      [[:x]           1]]

  (treelist data-children neg? {:foo {:bar [1 0] :baz [-1 0]}})
  )

(comment
  (require '[clojure.java.io :as io])
  (treelist #(map-indexed vector (sort (.listFiles %))) #(str/includes? % "o") (io/file "./src/docs_site/"))
  (treelist #(map-indexed vector (sort (.listFiles %))) #(str/includes? (str/lower-case %) "readme") (io/file "./src/"))
  )
