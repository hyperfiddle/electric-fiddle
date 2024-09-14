(ns dustingetz.million-checkboxes2
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-dom3-props :refer [#?(:cljs set-property!)]]
            [hyperfiddle.incseq :as i]
            [missionary.core :as m]))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

(e/defn Holding [lock F]
  (case (e/Task lock)
    (do (e/client (println "I have the lock")) (e/on-unmount #(lock)) (F))))

(defmacro holding [lock & body] `(Holding ~lock (e/fn [] ~@body)))

#?(:clj (def !xs (i/spine)))
#?(:clj (def !n (atom (long 100))))
(comment (reset! !n 100))

(e/defn Writer [!xs count target]
  (e/server
    ; smoothly grow number of elements
    (e/with-cycle [i (e/snapshot count)]
      (cond
        (< i target) (e/Task ((fn [] (!xs i {} [i false]) (m/sleep 10 (inc i)))))
        () i))

    (e/amb)))

#?(:clj (def lock (m/sem)))
(declare css)

(e/defn MillionCheckboxes2 []
  (dom/style (dom/text css))
  (let [xs (e/server (e/join !xs))
        count (e/server (e/input (i/count !xs)))]
    (e/server (holding lock (Writer !xs count (e/watch !n))))
    (dom/h1 (dom/text "10k dom elements (multiplayer). count: " count))
    (dom/div (dom/props {:class "board"})
      (e/for [[i x] xs]
        (let [node (.createElement js/document "div")] ; bypass mount-point - too heavy
          (dom/props node {:data-index i :data-checked x})
          (.appendChild dom/node node)))

      (let [mouse-down? (dom/Mouse-down?)]
        (e/for [[[i v] t] (dom/OnAll "mouseover"
                            (fn [e]
                              (when mouse-down?
                                (let [x (.-target e)]
                                  [(some-> (.getAttribute x "data-index") parse-long) ; defend mouseover board itself
                                   (some-> (.getAttribute x "data-checked") parse-boolean)]))))]
          (t (e/server (!xs i {} [i true]) i)) ; check
          (e/amb))))))

(def css "
.board div { width: 1em; height: 1em; display: inline-block; border: 1px #eee solid; }
.board div[data-checked=\"true\"] { background-color: red; }
.board { font-family: monospace; font-size: 7px; margin: 0; padding: 0; line-height: 0; }
")