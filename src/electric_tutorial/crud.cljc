(ns electric-tutorial.crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.forms :refer [Checkbox!]]))

(e/defn PendingMonitor [edits]
  (when (pos? (e/Count edits)) (dom/props {:aria-busy true}))
  edits)

(e/defn Input! [v & {:keys [maxlength]
                     :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      ; todo: submit on blur, cancel on esc
      (let [edits (dom/OnAll "keydown"
                    (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
                            (submit! [e] (when (= "Enter" (.-key e)) (read! (.-target e))))]
                      submit!))]
        (when-not (or (dom/Focused?) (pos? (e/Count edits)))
          (set! (.-value dom/node) v))
        edits))))

(e/defn Field [e a edits]
  (PendingMonitor
    (e/for [[t v] edits]
      [t [{:db/id e a v}]])))

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
      (case (e/server
              (case (e/Offload #(do (Thread/sleep 500) (d/transact! !conn tx))) ::ok))
        ::ok (t)))))