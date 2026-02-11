(ns dustingetz.talks.retry
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms5 :refer [Checkbox*]]))

(e/defn MonitorButton [label]
  (dom/button (dom/text label)
    (let [e (dom/On "click" identity nil)
          [t err] (e/Token e)]
      (dom/props {:aria-busy (some? t)
                  :disabled (some? t)
                  :aria-invalid (some? err)})
      t)))

(declare css)
(e/defn RetryDemo []
  (dom/style (dom/text css))
  (let [slow (dom/div (Checkbox* true :label "latency"))
        fail (dom/div (Checkbox* true :label "failure"))

        t (MonitorButton "commit!")]

    (when t
      (let [res (e/server
                  (e/Offload
                    (fn []
                      (when (true? slow) (Thread/sleep 1000))
                      (if fail ::rejected ::ok))))]
        (case res
          ::ok (t)
          (t res))))))

(def css "
[aria-busy=true] {background-color: yellow;}
[aria-invalid=true] {background-color: pink;}")