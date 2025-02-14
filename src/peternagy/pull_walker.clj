(ns peternagy.pull-walker)

;;; Given a pull expression
;;; - `hf-pull` the data based on this expression
;;; - `walker` to turn the pulled tree into a sequence of `[path value branch?]`
;;;
;;; Map traversal rules {:foo [:bar]}:
;;; - if traversing `:foo` returns a collection don't unroll further
;;; - if it's a map continue the traversal

(defn next-path
  ([path p] (with-meta (conj path p) (meta p)))
  ([path p o] (with-meta (conj path p) (merge (meta o) (meta p)))))

(defn ?expand-star [pull v]
  (if (some #{'*} pull)
    (-> (into [] (comp cat (distinct)) [(eduction (remove #{'*}) pull) (keys v)])
      (with-meta (meta pull)))
    pull))

(defn walker
  ([v pull] (walker v pull (fn [_k _v] true)))
  ([v pull keep?]
   ((fn rec [v pull path]
      (eduction (mapcat (fn [p]
                          (if (map? p)
                            (eduction (mapcat (fn [[k npull]]
                                                (let [nv (get v k), npath (next-path path k p)]
                                                  (if (map? nv)
                                                    (when-some [recv (seq (rec nv npull npath))]
                                                      (cons [npath nv true] recv))
                                                    (when (keep? k nv) [[npath nv false]])))))
                              p)
                            (let [nv (get v p)]
                              (when (keep? p nv) [[(next-path path p) nv false]])))))
        (?expand-star pull v)))
    v pull [])))
