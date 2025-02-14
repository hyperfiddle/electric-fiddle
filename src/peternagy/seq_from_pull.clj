(ns peternagy.seq-from-pull)

;;; Given a pull expression
;;; - `hf-pull` the data based on this expression
;;; - `seq-from-pull` to turn the pulled tree into a sequence of `[path value branch?]`
;;;
;;; Map traversal rules {:foo [:bar]}:
;;; - if traversing `:foo` returns a collection don't unroll further
;;; - if it's a map continue the traversal

(defn next-path
  ([path p] (with-meta (conj path p) (meta p)))
  ([path p o] (with-meta (conj path p) (merge (meta o) (meta p)))))

(defn seq-from-pull
  ([v pull] (seq-from-pull v pull (fn [_k _v] true)))
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
                            (if (= '* p)
                              (eduction (keep (fn [[k nv]] (when (keep? k nv) [(next-path path k p) nv false]))) v)
                              (let [nv (get v p)]
                                (when (keep? p nv) [[(next-path path p) nv false]]))))))
        pull))
    v pull [])))
