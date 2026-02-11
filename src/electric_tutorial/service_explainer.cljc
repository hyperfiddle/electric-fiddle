(ns electric-tutorial.service-explainer
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Checkbox*]]))

(e/defn TxButton [label]
  (dom/button (dom/text label)
    (let [e (dom/On "click" identity nil)
          [?t ?err] (e/Token e)]
      (dom/props {:aria-busy (some? ?t)
                  :disabled (some? ?t)
                  :aria-invalid (some? ?err)})
      ?t)))

(e/declare slow)
(e/declare fail)
(e/declare !db)

#?(:clj (defn slow-db-transact [!db slow fail]
          (when slow (Thread/sleep 800))
          (if fail
            (ex-info "transaction failed" {}) ; return error values; Electric v3 doesn't have try/catch yet
            (swap! !db not))))

(e/defn Service [?t]
  (e/client
    (when ?t
      (e/server
        (let [res (e/Offload #(slow-db-transact !db slow fail))
              ?err (ex-message res)]
          (e/client
            (if ?err (?t ?err) (?t))))))))

(declare css)
(e/defn TokenExplainer []
  (dom/style (dom/text css))
  (binding [!db (e/server (atom true))
            slow (dom/div (Checkbox* true :label "latency"))
            fail (dom/div (Checkbox* true :label "failure"))]
    slow fail

    (let [?t (TxButton "toggle!")]
      (Service ?t))

    (let [db (e/server (e/watch !db))]
      (dom/code (dom/text (pr-str db))))))

(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}")