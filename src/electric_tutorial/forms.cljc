(ns electric-tutorial.forms
  (:require [contrib.str :refer [pprint-str]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:cljs (defn read! [maxlength node]
           (when-some [v (not-empty (subs (.-value node) 0 maxlength))]
             (set! (.-value node) "") v)))

#?(:cljs (defn submit! [maxlength e]
           (when (= "Enter" (.-key e))
             (read! maxlength (.-target e)))))

(e/defn InputSubmit [& {:keys [maxlength] ; destr can cause roundtrips, fixme
                        :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (dom/OnAll "keydown" (partial submit! maxlength)))))

(e/defn Checkbox [checked label id]
  (e/client
    (let [id (or id (random-uuid))]
      (e/amb
        (dom/input (dom/props {:type "checkbox", :id id})
          (let [pending (dom/OnAll "change" #(-> % .-target .-checked))]
            (when-not (or (dom/Focused?) ; why? it's not an input
                         ; do not accept controlled values until caught up
                        (pos? (e/Count pending)))
              (set! (.-checked dom/node) checked))
            pending))
        (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn Monitor [log]
  (dom/pre (dom/props {:style {:display "grid" :grid-template-columns "1fr 1fr"}})
    (e/for [{:keys [::v ::pending]} log]
      (dom/div (dom/text (pprint-str v)))
      (dom/div (dom/props {:style {:text-align "right"}})
        (dom/text (if pending "pending" "ok"))))))

#?(:clj (def !log (atom [])))

(e/defn Forms []
  #_(dom/p (dom/text "Toggle the checkbox rapidly!"))
  (let [edits (e/amb
                (dom/div (Checkbox true "toggle me rapidly!" nil))
                (dom/div (InputSubmit :placeholder "Type a message" :maxlength 100)))]

    (e/for [[v t] edits]
      (case (e/server
              (case (e/Offload #(do (Thread/sleep 1000) (swap! !log conj v))) ::ok))
        ::ok (t)))

    (let [log (e/amb
                (e/server {::v (e/diff-by identity (e/watch !log))})
                (e/client (e/for [[v t] edits] {::v v ::pending true})))]
      (Monitor log))))