(ns dustingetz.treelister2
  (:require [clojure.zip :as zip]))

;; Navigation
(defn map-entry [k v] (first {k v}))
(defn records-coll? [?coll] (and (sequential? ?coll) (map? (first ?coll))))

(defn explorer-zipper [tree-root]
  (letfn [(branch? [x]
            (cond (map-entry? x)          (branch? (val x))
                  (records-coll? x)       false
                  (and (coll? x) (seq x)) true
                  ()                      false)) ; ensure boolean is returned
          (children [x]
            (cond (map? x)                      (seq x)
                  (map-entry? x)                (if (branch? x) (children (val x)) (val x))
                  (or (sequential? x) (set? x)) (map-indexed map-entry x)))] ; simple collections treated as indexed maps
    (zip/zipper branch? children (fn make-node [& _] (throw (ex-info "Edits not implemented" {}))) tree-root)))

;; Rendering
(defn node-path [zipper-node]
  (let [path (mapv key (filter map-entry? (zip/path zipper-node)))
        node (zip/node zipper-node)]
    (if (map-entry? node)
      (conj path (key node))
      path)))

(defn render-value [value]
  (cond (map-entry? value) (render-value (val value))
        (records-coll? value) (constantly value) ; <<<<<<<< thunk record cols (bad)
        () value))

(defn node-val [node] (when-not (zip/branch? node) (render-value (zip/node node))))

;; ----------

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
  (->> (explorer-zipper tree)
    (iterate zip/next) ; like `cc/tree-seq`, but remember path to nodes. Depth-first, in seq order, not recursive but still heap bound.
    (sequence ; fully lazy
      (comp (take-while (complement zip/end?)) ; stop at end of walk, if there is such end and it has been reached.
        (map (juxt node-path node-val zip/branch?)))) ; [path value branch?] – value is nil if branch? is true.
    (rest))) ; drop empty root node – value is always [() nil]

;;; Tests

(comment
  (explorer-seq nil) := nil
  (explorer-seq {}) := ()
  (explorer-seq []) := ()
  (explorer-seq #{}) := ()
  (explorer-seq {:k :v}) := '([[:k] :v false])
  (explorer-seq {:k [:v]}) := '([[:k] nil true] [[:k 0] :v false])
  (explorer-seq [:a :b]) := '([[0] :a false] [[1] :b false])
  (explorer-seq [:a {:b [{:c :d}]}])
  ;; := '([[0] :a false]
  ;;      [[1] nil true]
  ;;      [[1 :b] #function[clojure.core/constantly/fn--5759] false])

  )


(comment
  (explorer-seq test-data)
  := '([[:response] nil true]
       [[:response :Owner] nil true]
       [[:response :Owner :DisplayName] string false]
       [[:response :Owner :ID] string false]
       [[:response :Grants] nil true]
       [[:response :Grants :seq-of] nil true]
       [[:response :Grants :seq-of :Grantee] nil true]
       [[:response :Grants :seq-of :Grantee :DisplayName] string false]
       [[:response :Grants :seq-of :Grantee :EmailAddress] string false]
       [[:response :Grants :seq-of :Grantee :ID] string false]
       [[:response :Grants :seq-of :Grantee :Type] nil true]
       [[:response :Grants :seq-of :Grantee :Type 0] :one-of false]
       [[:response :Grants :seq-of :Grantee :Type 1] nil true]
       [[:response :Grants :seq-of :Grantee :Type 1 0] "CanonicalUser" false]
       [[:response :Grants :seq-of :Grantee :Type 1 1] "AmazonCustomerByEmail" false]
       [[:response :Grants :seq-of :Grantee :Type 1 2] "Group" false]
       [[:response :Grants :seq-of :Grantee :URI] string false]
       [[:response :Grants :seq-of :Permission] nil true]
       [[:response :Grants :seq-of :Permission 0] :one-of false]
       [[:response :Grants :seq-of :Permission 1] nil true]
       [[:response :Grants :seq-of :Permission 1 0] "FULL_CONTROL" false]
       [[:response :Grants :seq-of :Permission 1 1] "WRITE" false]
       [[:response :Grants :seq-of :Permission 1 2] "WRITE_ACP" false]
       [[:response :Grants :seq-of :Permission 1 3] "READ" false]
       [[:response :Grants :seq-of :Permission 1 4] "READ_ACP" false])

  (-> java.lang.management.ThreadMXBean
    (clojure.datafy/datafy)
    (explorer-seq))
  ;; := '([(:bases) nil true]
  ;;      [(:bases 0) java.lang.management.PlatformManagedObject nil] ; render should indent by (count path)
  ;;      [(:flags) nil true]
  ;;      [(:flags 0) :interface nil]
  ;;      [(:flags 1) :public nil]
  ;;      [(:flags 2) :abstract nil]
  ;;      [(:members) nil true]
  ;;      [(:members dumpAllThreads) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members findDeadlockedThreads) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members findMonitorDeadlockedThreads) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getAllThreadIds) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getCurrentThreadCpuTime) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getCurrentThreadUserTime) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getDaemonThreadCount) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getPeakThreadCount) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getThreadCount) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getThreadCpuTime) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getThreadInfo) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getThreadUserTime) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members getTotalStartedThreadCount) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isCurrentThreadCpuTimeSupported) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isObjectMonitorUsageSupported) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isSynchronizerUsageSupported) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isThreadContentionMonitoringEnabled) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isThreadContentionMonitoringSupported) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isThreadCpuTimeEnabled) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members isThreadCpuTimeSupported) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members resetPeakThreadCount) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members setThreadContentionMonitoringEnabled) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:members setThreadCpuTimeEnabled) #function[clojure.core/constantly/fn--5759] false]
  ;;      [(:name) java.lang.management.ThreadMXBean nil])
  )