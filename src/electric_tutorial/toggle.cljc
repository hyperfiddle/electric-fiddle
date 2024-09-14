(ns electric-tutorial.toggle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defonce !x (atom true))) ; server state

(e/defn NumberType [x]
  (case x
    true (e/client (pr-str (type 1))) ; javascript number type
    false (e/server (pr-str (type 1))))) ; java number type

(e/defn Toggle []
  (let [x (e/server (e/watch !x))]
    (dom/dl ; HTML <dl> - description list
      (dom/dt (dom/text "site")) (dom/dd (dom/text (if x "client" "server")))
      (dom/dt (dom/text "type")) (dom/dd (dom/text (NumberType x)))))

  (dom/button (dom/text "toggle client/server")
    (when-some [t (e/Token (dom/On "click"))]
      (dom/props {:disabled true})
      (t (e/server (swap! !x not))))))