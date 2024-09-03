(ns electric-tutorial.demo-toggle
  (:require [hyperfiddle.electric3 :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defonce !x (atom true))) ; server state

(e/defn Toggle []
  (e/client
    (let [x (e/server (e/watch !x))] ; reactive signal derived from atom
      (dom/div
        (dom/text "number type here is: "
          (case x
            true (e/client (pr-str (type 1))) ; javascript number type
            false (e/server (pr-str (type 1)))))) ; java number type

      (dom/div
        (dom/text "current site: "
          (if x
            "ClojureScript (client)"
            "Clojure (server)"))))

    (dom/button
      (dom/text "toggle client/server")
      (when-some [t ($ e/Token ($ dom/On "click"))]
        (dom/props {:disabled true})
        (t (e/server (swap! !x not)))))))
