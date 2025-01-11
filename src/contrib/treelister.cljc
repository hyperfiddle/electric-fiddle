(ns contrib.treelister
  (:require contrib.str
            [clojure.datafy :refer [datafy]]
            [hyperfiddle.rcf :refer [tests]]))

(defn- -tree-list [depth xs children-fn keep? input]
  (eduction
    (mapcat (fn [x]
              (let [x (datafy x)] ; ?
                (if-let [children (children-fn x)]
                  (when-let [rows (seq (-tree-list (inc depth) children children-fn keep? input))]
                    (into [[depth x]] rows))

                  ; leaf
                  (let [q []]
                    (if (keep? (doto x #_(prn 'keep-leaf?)) input)
                      (conj q [depth x])
                      q))))))
    xs))

(def any-matches? contrib.str/any-matches?) ; re-export for user convenience
(def includes-str? contrib.str/includes-str?)

(defn treelister
  ([xs] (treelister (fn [_]) contrib.str/includes-str? xs))
  ([children-fn xs] (treelister children-fn contrib.str/includes-str? xs)) ; don't make user :refer any-matches
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
  ((treelister
     simple-data-children
     contrib.str/includes-str?
     (datafy com.sun.management.ThreadMXBean)
     ) nil)

  (def x (first (:members (datafy Object))))
  (type (second x))

  (def x {:bases #{com.sun.management.ThreadMXBean}
          :flags #{:public}
          :methods {'clone [{:name 'clone}]}
          :name 'java.lang.Object})
  ((treelister simple-data-children contrib.str/includes-str? x) nil)

  ((treelister {:a nil}) nil)

  #_(any-matches? :a "") ; die
  )