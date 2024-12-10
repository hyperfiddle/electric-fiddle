(ns dustingetz.million-checkboxes
  #?(:cljs (:require-macros dustingetz.million-checkboxes))
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-dom3-props :refer [#?(:cljs set-property!)]]
            [hyperfiddle.incseq :as i]
            [missionary.core :as m]))

(e/defn Holding [lock F]
  (case (e/Task lock)
    (do (e/client (println "I have the lock")) (e/on-unmount #(lock)) (F))))

(defmacro holding [lock & body] `(Holding ~lock (e/fn [] ~@body)))

#?(:clj (def !xs (i/spine)))
#?(:clj (def !n (atom (long 200))))
(comment (reset! !n (long 10000)))

(defn randomly! [n f!]
  (m/sp (loop [] (m/? (m/sleep (rand-int n))) (f!) (recur))))

(defn thing [!xs !count]
  (m/ap
    (m/amb nil
      (loop []
        (m/? (m/sleep 100))
        (!xs (rand-int @!count) (fn [[i v] _] [i (not v)]) ::x)
        (recur)))))

(e/defn Writer [!xs count target]
  (e/server
    ; smoothly grow number of checkboxes
    (e/with-cycle [i (e/snapshot count)]
      (cond
        (< i target) (e/Task ((fn [] (!xs i {} [i false]) (m/sleep 10 (inc i)))))
        () i))

    (let [!count (atom (e/snapshot count))]
      (reset! !count count)
      (e/input (thing !xs !count)))

    (e/amb)))

#?(:clj (def lock (m/sem)))

(e/defn MillionCheckboxes []
  (let [xs (e/server (e/join !xs))
        count (e/server (e/input (i/count !xs)))]

    (e/server
      (holding lock
        (Writer !xs count (e/watch !n))))

    (dom/h1 (dom/text "count: " count))
    (e/Tap-diffs xs)

    (e/for [[i x] xs]
      ; electric-dom3 uses 5x more memory per element, todo investigate
      #_(dom/input (dom/props {:type "checkbox" :data-index i}) (set! (.-checked dom/node) x))

      ; optimize element creation
      (let [node (.createElement js/document "input")]
        (set-property! node :type "checkbox")
        (set-property! node :data-index i)
        (set! (.-checked node) x)
        (.appendChild dom/node node))

      (e/amb))

    (e/for [[t [i v]] (dom/On-all "change"
                        (fn [e] (let [x (.-target e)]
                                  [(long (.getAttribute x "data-index"))
                                   (.-checked x)])))]
      (case (e/server (!xs i {} [i v]) i)
        (t)))))

#_(e/server (e/Task (randomly! (* 100 1000) #(!xs i (fn [[i v] _] [i (not v)]) ::x))) (e/amb))