(ns peternagy.pull-tree
  (:require
    [contrib.debug :as dbg]))

(defn dumb-apply [sexp env]
  (let [[f$ & args] sexp
        f (resolve f$)
        args (replace env args)]
    (apply f args)))

(defn pull-key [v k env path cont]
  (let [npath (conj path k)]
    (if (seq? k)
      (let [nv (dumb-apply k env)]
        (cons [npath nv (boolean cont)]
          (when cont (cont nv npath))))
      (let [nv (get v k)]
        (cons [npath nv (boolean cont)]
          (when cont (cont nv npath)))))))

(defn pull-*-coll [v path cont]
  (eduction (map-indexed (fn [i nv] (cons [(conj path i) nv (boolean cont)]
                                      (when cont (cont nv (conj path i))))))
    cat v))

(defn pull-*-map [v path cont]
  (eduction (mapcat (fn [[k nv]] (cons [(conj path k) nv (boolean cont)]
                                   (when cont (cont nv (conj path k))))))
    v))

(defn as-vec [x] (cond (vector? x) x (nil? x) [] :else [x]))

(defn pull
  ([v expr] (pull v expr {}))
  ([v expr env] (pull v expr env []))
  ([v expr env path]
   (let [env (assoc env '% v)]
     (eduction (mapcat (fn [p]
                         (if (= '* p)
                           (if (map? v)
                             (pull-*-map v path nil)
                             (pull-*-coll v path nil))
                           (if (map? p)
                             (let [[np nexpr] (first p)
                                   cont (fn [v path] (pull v nexpr env path))]
                               (if (= '* np)
                                 (if (map? v)
                                   (pull-*-map v path cont)
                                   (pull-*-coll v path cont))
                                 (pull-key v np env path cont)))
                             (pull-key v p env path nil)))))
       (as-vec expr)))))

;;; alternate impl with instruction-set

(defn ->ir [expr]
  (into [] (mapcat (fn [p]
                     (if (map? p)
                       (let [[np nexpr] (first p)]
                         `[~(if (= '* np) [:descend-all] [:descend np])
                           ~@(->ir nexpr)
                           [:ascend]])
                       (if (= '* p) [[:read-all]] [[:read p]]))))
    (as-vec expr)))

(defn exec-ir [v ir]
  (loop [[inst & ir] ir, [v :as vstack] (list v), ret [], path []]
    (if inst
      (case (first inst)
        :descend (let [k (second inst), v (get v k), path (conj path k)]
                   (recur ir (cons v vstack) (conj ret [path v true]) path))
        :descend-all #_(if (map? v)
                       ))
      ret)
    ))
