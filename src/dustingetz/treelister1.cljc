(ns dustingetz.treelister1 "
treeseq recursion scheme which supports ergonomic search, i.e. retaining intermediate path nodes
for any matching leaves"
  (:require contrib.str
            [hyperfiddle.rcf :refer [tests]]))

(defn- -tree-list [depth xs children-fn keep?]
  (eduction
    (mapcat (fn [x]
              (if-let [children (children-fn x)]
                (when-let [rows (seq (-tree-list (inc depth) children children-fn keep?))]
                  (into [[depth x]] rows)) ; bug - accidentally elides empty folders

                ; leaf
                (let [q []]
                  (if (keep? (doto x #_(prn 'keep-leaf?)))
                    (conj q [depth x])
                    q)))))
    xs))

(def any-matches? contrib.str/any-matches?) ; re-export for user convenience
(def includes-str? contrib.str/includes-str?)

(defn treelister
  ([xs] (treelister (fn [_]) (constantly true) xs)) ; wtf
  ([children-fn xs] (treelister children-fn (constantly true) xs))
  ([children-fn keep? xs] (-tree-list 0 xs children-fn keep?)))

(tests
  (vec (treelister #(when (vector? %) %) odd? [1 2 [3 4] [5 [6 [7]]]]))
  := [[0 1]
      [0 [3 4]]
      [1 3]
      [0 [5 [6 [7]]]]
      [1 5]
      [1 [6 [7]]]
      [2 [7]]
      [3 7]]

  (treelister :children (fn [v] (-> v :file #{"a"}))
    [{:dir "x" :children [{:file "a"} {:file "b"}]}])
  (count (vec *1)) := 2

  "directory is omitted if there are no children matching keep?"
  (treelister :children (fn [v] (-> v :file #{"nope"}))
    [{:dir "x" :children [{:file "a"} {:file "b"}]}])
  (count (vec *1)) := 0)

(defn simple-data-children [x]
  #_(prn 'inspect x)
  (let [x x #_(datafy x)] ; turn methods into map?, etc
    (cond
      (map? x) (seq x)
      (map-entry? x) (simple-data-children (val x)) ; !
      (set? x) nil #_(seq x) ; only descend named values
      (and (sequential? x) (map? (first x))) nil #_(seq x)
      (and (sequential? x) (not (map? (first x)))) nil #_(seq x)
      () nil)))

(tests
  (simple-data-children nil) := nil
  (simple-data-children 1) := nil
  (simple-data-children [1 2]) := [1 2]
  (simple-data-children {:a 1 :b 2}) := [[:a 1] [:b 2]]
  (simple-data-children (first {:a 1})) := 1

  ; record collections?
  (simple-data-children [{:a 1}]) := [{:a 1}]

  ; skip sets to render inline?
  (simple-data-children #{1}) := nil
  (simple-data-children #{}) := nil
  (simple-data-children #{:a}) := nil
  (simple-data-children #{:a 1}) := nil
  (simple-data-children #{[1 2 3]}) := nil)

(comment
  (require '[clojure.datafy :refer [datafy]])
  (treelister simple-data-children (constantly true)
    (datafy com.sun.management.ThreadMXBean))

  (def x (first (:members (datafy Object))))
  (type (second x))

  (def x {:bases #{com.sun.management.ThreadMXBean}
          :flags #{:public}
          :methods {'clone [{:name 'clone}]}
          :name 'java.lang.Object})
  (treelister simple-data-children x)

  "no children?"
  (treelister {:a nil}) := [[0 [:a nil]]] ; why
  )