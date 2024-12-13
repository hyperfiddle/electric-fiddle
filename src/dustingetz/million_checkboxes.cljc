(ns dustingetz.million-checkboxes
  #?(:cljs (:require-macros dustingetz.million-checkboxes))
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-dom3-props :refer [#?(:cljs set-property!)]]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.incseq :as i]
            [missionary.core :as m]))

(e/defn Holding [lock F]
  (case (e/Task lock)
    (do (e/client (println "I have the lock")) (e/on-unmount #(lock)) (F))))

(defmacro holding [lock & body] `(Holding ~lock (e/fn [] ~@body)))

(defn randomly! [n f!]
  (m/sp (loop [] (m/? (m/sleep (rand-int n))) (f!) (recur))))

(defn thing [!xs !count]
  (m/ap
    (m/amb nil
      (loop []
        (m/? (m/sleep 100))
        (let [c @!count]
          (dotimes [_ (/ c 100)]
            (!xs (rand-int c) (fn [[i v] _] [i (not v)]) ::x)))
        (recur)))))

(e/defn Writer [!xs count target &
                {:keys [delay]
                 :or {delay 10}}]
  (e/server
    (e/with-cycle [n (e/snapshot count)] ; smoothly grow n number of checkboxes towards target
      (cond
        (< n target) (e/Task ((fn [] (!xs n {} [n false]) (m/sleep delay (inc n)))))
        (> n target) (e/Task ((fn [] (!xs (dec n) {} nil) (m/sleep delay (dec n)))))
        () n))
    (e/input (thing !xs (doto (atom (e/snapshot count)) (reset! count)))) ; toggle randomly
    (e/amb)))

#?(:clj (def lock (m/sem)))
#?(:clj (def !xs (i/spine))) ; shared

(e/defn MillionCheckboxes []
  (let [xs (e/server (e/join !xs))
        count (e/server (e/Count xs))
        target (dom/h1 (dom/text "count: " count " target: " ) (parse-long (Input* 200 :type "number")))]
    target

    (e/server (holding lock (Writer !xs count target :delay 3)))

    (e/for [[i x] xs]
      ; electric-dom3 uses 5x more memory per element, todo investigate
      (dom/input (dom/props {:type "checkbox" :data-index i}) (set! (.-checked dom/node) x))

      ; optimize element creation
      #_(let [node (.createElement js/document "input")]
        (set-property! node :type "checkbox")
        (set-property! node :data-index i)
        (set! (.-checked node) x)
        (.appendChild dom/node node))
      (e/amb))

    (e/for [[t [i v]] (dom/On-all "change"
                        (fn [e] (let [x (.-target e)]
                                  [(long (.getAttribute x "data-index"))
                                   (.-checked x)])))]
      (case (e/server (!xs i {} [i v]) i) (t)))))

#_(e/server (e/Task (randomly! (* 100 1000) #(!xs i (fn [[i v] _] [i (not v)]) ::x))) (e/amb))