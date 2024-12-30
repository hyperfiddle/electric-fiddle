(ns datomic-browser.contrib
  #?(:cljs (:require-macros datomic-browser.contrib))
  (:require [clojure.datafy :refer [datafy]]
            [hyperfiddle.rcf :refer [tests]]
            [missionary.core :as m]))

(defn clamp-left [n left] (max n left)) ; when under limit, clamp up to larger

(defn unqualify
  "Strip namespace from keyword, discarding it and return unqualified keyword. Nil-safe.
  (unqualify :db.type/ref) -> :ref"
  [?kw]
  (assert (or (nil? ?kw) (keyword? ?kw) (symbol? ?kw)) (str " can't unqualify: " ?kw))
  (cond
    (nil? ?kw) nil
    (keyword? ?kw) (keyword (name ?kw))
    (symbol? ?kw) (symbol (name ?kw))))

(tests
  (unqualify :db.type/ref) := :ref
  (unqualify ::x) := :x
  (unqualify :x) := :x
  (unqualify `x) := 'x
  (unqualify 'x) := 'x
  (unqualify "") :throws #?(:clj AssertionError :cljs js/Error)
  (unqualify nil) := nil)

(defn- error! [pred-expr v-expr v ex-data]
  (let [msg (str "check failed: (" (pr-str pred-expr) " " (pr-str v-expr) ") for " (pr-str v))]
    (throw (ex-info msg ex-data)))) ; todo elide top frames

(defn -check [pred-expr pred v-expr v ex-data]
  (cond
    (keyword? pred) (when-not (or (= pred v) ; special rule - keyword equality
                                (pred v))
                      (error! pred-expr v-expr v ex-data))
    () (when-not (pred v)
         (error! pred-expr v-expr v ex-data)))
  v)

(defmacro check
  ([v] `(check some? ~v))
  ([pred v] `(check ~pred ~v {}))
  ([pred v ex-data] `(-check '~pred ~pred '~v ~v ~ex-data)))

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


(defn flatten-nested ; claude generated this
  ([data] (flatten-nested data []))
  ([data path]
   (cond
     (map? data)
     (mapcat (fn [[k v]]
               (cond
                 (map? v)
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 (and (sequential? v) (map? (first v)))
                 #_[{:path path :name k :value '...}] ; elide collections of records
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 ; render simple collections inline
                 (and (sequential? v) (not (map? (first v))))
                 [{:path path :name k :value v}]

                 #_#_(nil? v) [{:path path :name k}]
                 () [{:path path :name k :value v}]))
       data)

     ; render simple collections as indexed maps
     (sequential? data)
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:path path :name i}
                   (flatten-nested v (conj path i)))

                 ()
                 [{:path path :name i :value v}]))
       (range) data)

     ; what else?
     () [{:path path :value data}])))

(comment
  (def test-data
    '{:response
      {:Owner
       {:DisplayName string
        :ID string}
       :Grants
       {:seq-of
        {:Grantee
         {:DisplayName string
          :EmailAddress string
          :ID string
          :Type [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]
          :URI string}
         :Permission [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]}}}})
  (flatten-nested test-data)
  )

(defn seq-consumer [xs] ; xs is iterable
  (m/ap
    (loop [xs xs]
      (if (m/? (m/via m/blk (seq xs)))
        (m/amb (m/? (m/via m/blk (first xs))) (recur (rest xs)))
        (m/amb)))))
