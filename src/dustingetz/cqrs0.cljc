(ns dustingetz.cqrs0
  (:require [contrib.data :refer [index-by]]
            [hyperfiddle.electric3 :as e]))

(e/defn Service [effects edits]
  (e/client ; bias for writes because token doesn't transfer
    (e/for [[t xcmd _] edits]
      (case (e/server
              (when-some [effect (effects xcmd)] ; secure
                (case (e/Task effect) ::ok)))
        ::ok (t)))))

(e/defn Reconcile-records [stable-kf sort-key as bs]
  (e/client
    (let [as! (e/as-vec as) ; todo differential reconciliation
          bs! (e/as-vec bs)]
      (->> (merge
             (index-by stable-kf as!)
             (index-by stable-kf bs!))
        vals
        (sort-by sort-key)
        #_(drop (count bs!)) ; todo fix glitch
        (e/diff-by stable-kf)))))

(e/defn PendingController [kf sort-key edits xs]
  (let [!pending (atom {}) ; [id -> prediction]
        ps (val (e/diff-by key (e/watch !pending)))]
    (e/for [[t xcmd predictions] edits]
      (assert (= 1 (count predictions)))
      (let [[id record] (first predictions)]
        (prn 'pending-cmd xcmd)
        (swap! !pending assoc id (assoc record ::pending true))
        (e/on-unmount #(swap! !pending dissoc id))
        (e/amb)))
    (Reconcile-records kf sort-key xs ps)))