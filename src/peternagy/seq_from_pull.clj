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
  ([v pull] (seq-from-pull v pull []))
  ([v pull path]
   (eduction (mapcat (fn [p]
                       (if (map? p)
                         (eduction (mapcat (fn [[k npull]]
                                             (let [nv (get v k), npath (next-path path k p)]
                                               (if (map? nv)
                                                 (cons [npath nv true] (seq-from-pull nv npull npath))
                                                 [[npath nv false]]))))
                           p)
                         (if (= '* p)
                           (eduction (map (fn [[k nv]] [(next-path path k p) nv false])) v)
                           [[(next-path path p) (get v p) false]]))))
     pull)))
