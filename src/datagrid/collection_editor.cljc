(ns datagrid.collection-editor
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.incseq :as incseq]))

(defn rotate [from to degree]
  [degree (assoc (incseq/empty-diff degree) :permutation (incseq/rotation from to))])

(defn delete! [index degree]
  (let [event    (incseq/empty-diff degree)
        rotation (incseq/rotation index (dec degree))
        deletion (assoc event :shrink 1)]
    (if-not (empty? rotation)
      [(dec degree) (incseq/combine (assoc event :permutation rotation) deletion)]
      [(dec degree) deletion])))

(defn create!
  ([degree] (create! degree degree))
  ([index degree] ; create at the end
   (let [base (incseq/empty-diff (inc degree))
         grow (assoc base :grow 1)
         diff (if (= index degree)
                grow
                (incseq/combine grow (assoc base :permutation (incseq/rotation degree (inc index)))))]
     [(inc degree) diff])))

(defn change [index value degree]
  [degree (assoc (incseq/empty-diff degree) :change {index value})])

(defn undo! [{::keys [prev-states current-degree current-diff] :as current-state}]
  (if-some [prev (first prev-states)]
    (let [[prev-degree prev-diff] prev]
      (-> (update current-state ::prev-states rest)
        (update ::next-states conj [current-degree current-diff])
        (assoc ::current-degree prev-degree ::current-diff prev-diff)))
    current-state))

(defn redo! [{::keys [next-states current-degree current-diff] :as current-state}]
  (if-some [next (first next-states)]
    (let [[next-degree next-diff] next]
      (-> (update current-state ::next-states rest)
        (update ::prev-states conj [current-degree current-diff])
        (assoc ::current-degree next-degree ::current-diff next-diff)))
    current-state))

(defn collection-editor [managed-collection-count]
  (let [!state   (atom {::current-degree managed-collection-count
                        ::current-diff   (incseq/empty-diff managed-collection-count)
                        ::prev-states    ()
                        ::next-states    ()})
        perform! (fn [action]
                   (swap! !state (fn [{::keys [current-degree current-diff] :as current-state}]
                                   (let [[next-degree next-diff] (action current-degree)]
                                     (-> current-state
                                       (assoc ::current-degree next-degree
                                         ::current-diff   (incseq/combine current-diff next-diff))
                                       (update ::prev-states conj [current-degree current-diff])
                                       (update ::next-states empty)))))
                   nil)]
    {::rotate! (fn [from to] (perform! (partial rotate from to)))
     ::create! (fn ([] (perform! create!))
                 ([index] (perform! (partial create! index))))
     ::delete! (fn [index] (perform! (partial delete! index)))
     ::change! (fn [index value] (perform! (partial change index value)))
     ::undo!   (fn [] (swap! !state undo!) nil)
     ::redo!   (fn [] (swap! !state redo!) nil)
     ::!state  !state}))

(e/defn* CollectionEditor [collection]
  (let [coll-count (count collection)
        {::keys [rotate! create! delete! change! undo! redo! !state]} (collection-editor coll-count)
        diff                                                          (::current-diff (e/watch !state))]
    {::rows    (incseq/patch-vec collection diff)
     ::rotate! rotate!
     ::create! create!
     ::delete! delete!
     ::change! change!
     ::undo!   undo!
     ::redo!   redo!
     ::edited? (not= diff (incseq/empty-diff coll-count))
     ::diff    diff}))
