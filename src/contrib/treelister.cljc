(ns contrib.treelister
  (:require [clojure.datafy :refer [datafy]]
            [hyperfiddle.rcf :refer [tests]]))

(defn- -tree-list [depth xs children-fn keep? input]
  (eduction (mapcat (fn [x]
                      (let [x (datafy x)]
                        (if-let [children (children-fn x)]
                          (when-let [rows (seq (-tree-list (inc depth) children children-fn keep? input))]
                            (into [[depth x]] rows))
                          (cond-> [] (keep? x input) (conj [depth x]))))))
    (datafy xs)))

(defn- any-matches? [coll needle] ; duplicate of contrib.str/any-matches?
  (let [substr (clojure.string/lower-case (str needle))]
    (some #(when % (clojure.string/includes? (clojure.string/lower-case (str %)) substr)) coll)))

(defn treelister
  ([xs] (treelister (fn [_]) any-matches? xs))
  ([children-fn xs] (treelister children-fn any-matches? xs)) ; don't make user :refer any-matches
  ([children-fn keep? xs] (fn [input] (-tree-list 0 xs children-fn keep? input))))

(tests
  (vec ((treelister #(when (vector? %) %) (fn [v _] (odd? v))
          [1 2 [3 4] [5 [6 [7]]]]) nil))
  := [[0 1] [0 [3 4]] [1 3] [0 [5 [6 [7]]]] [1 5] [1 [6 [7]]] [2 [7]] [3 7]]

  ((treelister :children (fn [v needle] (-> v :file #{needle}))
     [{:dir "x" :children [{:file "a"} {:file "b"}]}]) "a")
  (count (vec *1)) := 2

  "directory is omitted if there are no children matching keep?"
  ((treelister :children (fn [v needle] (-> v :file #{needle}))
     [{:dir "x" :children [{:file "a"} {:file "b"}]}]) "nope")
  (count (vec *1)) := 0)
