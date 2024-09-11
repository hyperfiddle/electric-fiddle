(ns electric-tutorial.forms
  (:require [contrib.str :refer [pprint-str]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn InputSubmit! [& {:keys [maxlength]
                         :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (dom/OnAll "keydown"
        (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
                (read-clear! [node] (when-some [v (read! node)] (set! (.-value node) "") v))
                (submit! [e] (when (= "Enter" (.-key e)) (read-clear! (.-target e))))]
          submit!)))))

(e/defn Checkbox! [label & [id]]
  (e/client
    (let [id (or id (random-uuid))]
      (e/amb
        (dom/input (dom/props {:type "checkbox", :id id})
          (dom/OnAll "change" #(-> % .-target .-checked)))
        (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn Monitor [log]
  (dom/pre (dom/props {:style {:display "grid" :grid-template-columns "1fr 1fr"}})
    (e/for [{:keys [::v ::pending]} log]
      (dom/div (dom/text (pprint-str v)))
      (dom/div (dom/props {:style {:text-align "right"}})
        (dom/text (if pending "pending" "ok"))))))

#?(:clj (def !log (atom [])))

(e/defn Forms []
  (let [edits (e/amb
                (dom/div (Checkbox! "toggle me rapidly!"))
                (dom/div (InputSubmit! :placeholder "Message rapidly" :maxlength 100)))]

    (e/for [[v t] edits]
      (case (e/server
              (case (e/Offload #(do (Thread/sleep 1000) (swap! !log conj v))) ::ok))
        ::ok (t)))

    (let [log (e/amb
                (e/server {::v (e/diff-by identity (e/watch !log))})
                (e/client (e/for [[v t] edits] {::v v ::pending true})))]
      (Monitor log))))