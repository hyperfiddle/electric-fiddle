(ns dustingetz.treelister2
  (:require [clojure.zip :as zip]))

;; Navigation
(defn map-entry [k v] (first {k v}))
(defn records-coll? [?coll] (and (sequential? ?coll) (map? (first ?coll))))

;; Rendering
(defn node-path [zipper-node]
  (let [path (mapv key (filter map-entry? (zip/path zipper-node)))
        node (zip/node zipper-node)]
    (if (map-entry? node)
      (conj path (key node))
      path)))

(defn node-val [render-value node] (render-value (zip/node node)))

(defn tree-seq2 [tree branch? children render-value]
  (->> tree
    (zip/zipper branch? children (fn make-node [& _] (throw (ex-info "Edits not implemented" {}))))
    (iterate zip/next) ; like `cc/tree-seq`, but remember path to nodes. Depth-first, in seq order, not recursive but still heap bound.
    (sequence ; fully lazy
      (comp (take-while (complement zip/end?)) ; stop at end of walk, if there is such end and it has been reached.
        #_(remove (fn [x] (some-> x zip/up zip/node children empty?))) ; SLOW, is this accidentally polynomial?
        #_(filter zip/node) ; remove nils - coupling?
        (map (juxt node-path (partial node-val render-value) zip/branch?))))))

;; --- Example usage

(defn explorer-seq
  ;; Improvements over flatten-nested:
  ;; - render nested non-record sequences as indexed seqs - flatten-nested only supports it at root level
  ;; - separate traversal from rendering
  ;; - can lazily traverse deep nested trees (or cyclic graphs) - impl doesn't use recursion, but still heap-bound as we accumulate `path`.
  ;; - can theoretically walk up, backwards, follow symlinks, etc. but no use case ATM.
  "Produce a lazy sequence of ([path value branch?] ...) representing a recursive depth-first walk over maps and sequences, in seq order.
  Sequences of records (seqs of maps) are not walked and produces a thunk: {:a [{:b :c}]} -> ([[:a] (constantly [{:b :c}]) false]])
  Other sequences are treated as indexed maps: [:a :b] -> ([[0] :a false], [[1] :b false]).
  Empty sequences are treated as plain values and are not walked.
  `branch?` allows renderer to distinguish nil value vs no value."
  [tree]
  (letfn [(branch? [x]
            (cond (map-entry? x) (branch? (val x))
                  (records-coll? x) false
                  (and (coll? x) (seq x)) true
                  () false)) ; ensure boolean is returned
          (children [x]
            (cond (map? x) (seq x)
                  (map-entry? x) (if (branch? x) (children (val x)) (val x))
                  (or (sequential? x) (set? x)) (map-indexed map-entry x))) ; simple collections treated as indexed maps
          (render-value [value]
            (cond (map-entry? value) (render-value (val value))
                  (records-coll? value) (constantly value) ; <<<<<<<< thunk record cols (bad)
                  () value))]
    (tree-seq2 tree branch? children render-value)))

;;; Tests

(comment
  (explorer-seq nil) := [[[] nil false]] ; emit root of tree - caller can drop
  (explorer-seq {}) := [[[] {} false]]
  (explorer-seq []) := [[[] [] false]]
  (explorer-seq #{}) := [[[] #{} false]]
  (explorer-seq {:k :v})
  := [[[] {:k :v} true]
      [[:k] :v false]]
  (explorer-seq {:k [:v]})
  := [[[] {:k [:v]} true]
      [[:k] [:v] true]
      [[:k 0] :v false]]
  (explorer-seq [:a :b])
  := [[[] [:a :b] true]
      [[0] :a false]
      [[1] :b false]]
  (explorer-seq [:a {:b [{:c :d}]}])
  := [[[] [:a {:b [{:c :d}]}] true]
      [[0] :a false]
      [[1] {:b [{:c :d}]} true]
      [[1 :b] _ false]] ; record-col thunk
  )

(comment
  (explorer-seq test-data)
  (-> java.lang.management.ThreadMXBean (clojure.datafy/datafy) (explorer-seq))
  )