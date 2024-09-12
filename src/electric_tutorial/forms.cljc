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

(e/defn Checkbox! [checked & {:keys [id label] :as props
                              :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
        (let [pending (dom/OnAll "change" #(-> % .-target .-checked))]
          (when-not (or (dom/Focused?) (pos? (e/Count pending)))
            (set! (.-checked dom/node) checked))
          pending))
      (e/When label
        (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn Monitor [!log edits]
  (let [log (e/amb
              (e/server {::v (e/diff-by identity (e/watch !log))})
              (e/client (e/for [[t v] edits] {::v v ::pending true})))]
    (dom/pre (dom/props {:style {:display "grid" :grid-template-columns "1fr 1fr"}})
      (e/for [{:keys [::v ::pending]} log]
        (dom/div (dom/text (pprint-str v)))
        (dom/div (dom/props {:style {:text-align "right"}})
          (dom/text (if pending "pending" "ok")))))))

(e/defn Forms []
  (let [!log (e/server (atom []))
        edits (e/amb
                (dom/div (Checkbox! true :label "toggle me rapidly!"))
                (dom/div (InputSubmit! :placeholder "Message rapidly" :maxlength 100)))]

    (Monitor !log edits)

    (e/for [[t v] #_(Filter some?) edits]
      (case (e/server
              (case (e/Offload #(do (Thread/sleep 500) (swap! !log conj v))) ::ok))
        ::ok (t)))))