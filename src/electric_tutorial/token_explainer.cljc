(ns electric-tutorial.token-explainer
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Checkbox*]]))

(declare css)
(e/defn TokenExplainer []
  (dom/style (dom/text css))
  (let [slow (dom/div (Checkbox* true :label "latency"))
        fail (dom/div (Checkbox* true :label "failure"))
        !x (e/server (atom true)) x (e/server (e/watch !x))]
    slow fail
    (when-some [t (dom/button (dom/text "toggle!")
                    (let [e (dom/On "click" identity nil)
                          [t err] (e/Token e)]
                      (dom/props {:aria-busy (some? t)
                                  :disabled (some? t)
                                  :aria-invalid (some? err)})
                      t))]
      (let [res (e/server
                  (e/Offload
                    (fn []
                      (when (true? slow) (Thread/sleep 1000))
                      (if fail ::rejected (do (swap! !x not) ::ok)))))]
        (case res
          ::ok (t) ; success sentinel
          (t res)))) ; feed error back into originating control
    (dom/code (dom/text (pr-str x)))))

(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}")