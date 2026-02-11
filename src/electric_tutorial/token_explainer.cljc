(ns electric-tutorial.token-explainer
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn TxButton [label]
  (dom/button (dom/text label)
    (let [e (dom/On "click" identity nil)
          [?t ?err] (e/Token e)]
      (when ?err (dom/text " " ?err))
      (dom/props {:aria-busy (some? ?t)
                  :disabled (some? ?t)
                  :aria-invalid (some? ?err)})
      ?t)))

(declare css)
(e/defn TokenExplainer []
  (dom/style (dom/text css))
  (when-some [t (TxButton "transact!")]
    (if (e/server (do (Thread/sleep 800) (rand-nth [true false])))
      (t) ; success
      (t "rejected")))) ; on failure, feed error back into originating control

(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}")