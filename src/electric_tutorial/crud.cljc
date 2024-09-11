(ns electric-tutorial.crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

; Big idea:
; someone, somewhere, needs to submit atomic edit transactions to the server.
; "submit" implies a submit/discard interaction (e.g. enter/esc, or a button) to submit or discard the edit.
; Submission can be field level (enter/esc), or form level, or higher
; Submission implies a token, typically from dom/OnAll, attached to the submit control element.
; field level means - dom/OnAll at field, submit/discard is implicit like a spreadsheet.
; form level means - dom/On inside fields, collect values, explicit submit/discard buttons.

(e/defn Input [v & {:keys [maxlength]
                    :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      ; todo: submit on blur, cancel on esc
      (let [pending (dom/OnAll "keydown"
                      (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
                              (submit! [e] (when (= "Enter" (.-key e)) (read! (.-target e))))]
                        submit!))]
        (when-not (or (dom/Focused?) (pos? (e/Count pending)))
          (set! (.-value dom/node) v))
        pending))))

(e/defn Checkbox [checked label id]
  (e/client
    (let [id (or id (random-uuid))]
      (e/amb
        (dom/input (dom/props {:type "checkbox", :id id})
          (let [pending (dom/OnAll "change" #(-> % .-target .-checked))]
            (when-not (or (dom/Focused?) (pos? (e/Count pending)))
              (set! (.-checked dom/node) checked))
            pending))
        (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn Field [e a edits]
  (e/for [[v t] edits]
    [t [{:db/id e a v}]]))

(e/defn PendingMonitor [edits]
  (dom/props {:style {:background-color (when (pos? (e/Count edits)) "yellow")}})
  edits)

(e/defn Form [{:keys [db/id ::x-bool ::x-str]}]
  (e/amb
    (dom/div (PendingMonitor (Field id ::x-bool (Checkbox x-bool "toggle me rapidly!" nil))))
    (dom/div (PendingMonitor (Field id ::x-str (Input x-str :placeholder "Message rapidly" :maxlength 100))))))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact!
                           [{:db/id 42 ::x-bool true ::x-str "hello"}]))))

(e/defn Crud []
  (let [db (e/server (e/watch !conn))
        record (e/server (d/pull db [:db/id ::x-bool ::x-str] 42))
        edits (dom/div (dom/props {:style {:display "grid" :grid-template-columns "1fr 1fr"}})
                (e/amb
                  (dom/div (Form record))
                  (dom/div (Form record))))]
    (e/for [[t tx] edits]
      (case (e/server
              (case (e/Offload #(do (Thread/sleep 1000) (d/transact! !conn tx))) ::ok))
        ::ok (t)))))