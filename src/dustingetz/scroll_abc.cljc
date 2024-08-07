(ns dustingetz.scroll-abc
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :as rcf :refer [tests % tap with]]))

(e/defn Tap-diffs [tap! x] (doto x (-> e/pure e/input tap!)))

(defn abc
  ([N] (abc N 0))
  ([N start] (vec (for [j (range N)]
                    (char (-> (+ start j) #_(mod 26) (+ 97)))))))

(defn abbc [N]
  (vec (for [i (range N)]
         (abc N i))))
(tests (abbc 3) := [[\a \b \c] [\b \c \d] [\c \d \e]])

(defn window [xs offset limit]
  (subvec (vec xs) ; fast cast
    (Math/max offset 0)
    (Math/min (+ offset limit) (count xs))))

(e/defn Window [xs! kf offset limit]
  (e/diff-by kf (window (vec xs!) offset limit)))

(def record-count 100)
(def !limit-x (atom 60))
(def !limit-y (atom 24))
(def !offset-x (atom 0))
(def !offset-y (atom 0))

(comment
  (swap! !offset-y inc)
  (swap! !offset-y dec)
  (swap! !offset-x inc)
  (swap! !offset-x dec)

  (swap! !limit-y inc)
  (swap! !limit-y dec)
  (swap! !limit-x inc)
  (swap! !limit-x dec))

; goal: fullscreen datagrid with high performance scroll over ~100 physical rows

(e/defn Scroll1 []
  (e/client
    (dom/props {:style {:font-family "monospace"}})
    (let [xs (e/server (abc record-count))
          offset (e/server (e/watch !offset-y))
          limit (e/server (e/watch !limit-y))]
      (e/cursor [x (e/server (e/diff-by identity (window xs offset limit)))]
        (dom/div
          (dom/text x #_(doto x println)))))))

(e/defn Scroll2 [] ; infinite loop
  (e/client
    (dom/props {:style {:font-family "monospace"}})
    (let [xy (e/server (abbc record-count))]
      (e/cursor [j (e/server (e/diff-by identity (window (range record-count)
                                                   (e/watch !offset-y)
                                                   (e/watch !limit-y))))]
        (dom/div
          (e/cursor [i (e/server (e/diff-by identity (window (range record-count)
                                                       (e/watch !offset-x)
                                                       (e/watch !limit-x))))]
            (dom/text (e/server (get-in xy [i j])))))))))

(e/defn Scroll3 []
  (e/client
    (dom/props {:style {:font-family "monospace"}})
    (dom/div
      (let [xy (e/server (abbc record-count))
            js (e/server (e/diff-by identity (window (range record-count)
                                               (e/watch !offset-y)
                                               (e/watch !limit-y))))
            is (e/server (e/diff-by identity (window (range record-count)
                                               (e/watch !offset-x)
                                               (e/watch !limit-x))))]
        (println (get-in xy [js is]))))))

(e/defn Scroll4 [] ; is there a perf difference between pointer indexing and direct diffs?
  (e/client
    (dom/props {:style {:font-family "monospace"}})
    (dom/div
      (let [xy (e/server (abbc record-count))]
        (e/cursor [xs (e/server (e/diff-by identity (window xy
                                                      (e/watch !offset-y)
                                                      (e/watch !limit-y))))]
          (dom/div
            (e/cursor [x (e/server (e/diff-by identity (window xs
                                                         (e/watch !offset-x)
                                                         (e/watch !limit-x))))]
              (dom/text x))))))))
