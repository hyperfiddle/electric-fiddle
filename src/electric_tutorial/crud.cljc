(ns electric-tutorial.crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.input-zoo :refer [Input!]]
            [electric-tutorial.forms :refer [Checkbox!]]))

(e/defn PendingMonitor [edits]
  (when (pos? (e/Count edits)) (dom/props {:aria-busy true}))
  edits)

(e/defn Field [e a edits]
  (PendingMonitor
    (e/for [[t v] edits]
      [t [{:db/id e a v}]]))) ; insecure by design - txns from client authority

(e/defn Form [{:keys [db/id ::x-bool ::x-str]}]
  (e/amb
    (dom/div (Field id ::x-bool (Checkbox! x-bool :label "toggle me rapidly!")))
    (dom/div (Field id ::x-str (Input! x-str :placeholder "Message rapidly")))))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 ::x-bool true ::x-str "hello"}]))))

(e/defn Crud []
  (let [db (e/server (e/watch !conn))
        record (e/server (d/pull db [:db/id ::x-bool ::x-str] 42))
        edits (dom/div (dom/props {:style {:display "grid" :grid-template-columns "1fr 1fr"}})
                (e/amb
                  (dom/div (Form record))
                  (dom/div (Form record))))]
    (e/for [[t tx] edits]
      (case (e/server ; insecure by design - wide open crud endpoint
              (case (e/Offload #(do (Thread/sleep 500) (d/transact! !conn tx))) ::ok))
        ::ok (t)))))