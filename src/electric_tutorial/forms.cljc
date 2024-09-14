(ns electric-tutorial.forms
  #?(:cljs (:require-macros electric-tutorial.forms))
  (:require [contrib.str :refer [pprint-str]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.input-zoo :refer [Input! PendingMonitor]]))

; commit/discard with staging area
; inputs - emit as you type - with a token
; stage - monitor edits and accumulate them
; button - batch all edits into single token, chained with upstream tokens

(e/defn Checkbox! [checked & {:keys [id label] :as props
                              :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (dom/div (dom/props {:style {:width "fit-content"}}) ; for yellow background
        (PendingMonitor ; checkboxes don't have background so style wrapper div
          (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
            (let [e (dom/On "change" identity) t (e/Token e)]
              (when-not (or (dom/Focused?) (some? t)) (set! (.-checked dom/node) checked))
              (if t [t (-> e .-target .-checked)] (e/amb))))))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn Field* [e a Control]
  (e/amb
    (dom/dt (dom/text (name a)))
    (dom/dd (e/for [[t v] (Control)]
              [t [{:db/id e a v}]])))) ; insecure by design - txns from client authority

(defmacro Field [e a body] `(Field* ~e ~a (e/fn [] ~body)))

(e/defn UserForm [{:keys [db/id user/x-bool user/x-str1 user/x-str2]}]
  (dom/dl
    (e/amb
      (Field id :user/x-str1 (Input! x-str1))
      (Field id :user/x-str2 (Input! x-str2))
      (Field id :user/x-bool (Checkbox! x-bool)))))

(defn merge-tx [& txs] (vec (apply concat txs)))

(e/defn Button! [directive & {:keys [label disabled] :as props}]
  (dom/button (dom/text label) (dom/props (dissoc props :disabled :label))
    (let [t (e/Token (dom/On "click"))]
      (dom/props {:disabled (or disabled (some? t))})
      (e/When (some? t) [t directive]))))

(e/defn Branch [[ts txs :as edits]] ; amb destructure
  (let [batch (apply merge-tx (e/as-vec txs))
        reset (let [ts (e/as-vec ts)] #(doseq [t ts] (t)))
        n (e/Count edits)]
    (e/amb
      (e/for [[t cmd] (e/amb
                        (Button! ::commit :label "commit" :disabled (zero? n)) ; todo progress
                        (Button! ::discard :label "discard" :disabled (zero? n))
                        (dom/span (dom/text " " n " dirty")))]
        (case cmd
          ::discard (case (t (reset)) (e/amb)) ; clear edits, controlled form will reset
          ::commit [#(t (reset)) batch])) ; commit as atomic batch

      (dom/pre (dom/props {:style {:min-height "4em"}})
        (dom/text (pprint-str batch))))))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/x-bool true :user/x-str1 "one" :user/x-str2 "two"}]))))

(e/defn Forms []
  (let [db (e/server (e/watch !conn))
        record (e/server (d/pull db [:db/id :user/x-bool :user/x-str1 :user/x-str2] 42))
        edits (dom/div
                (e/amb
                  (dom/fieldset (Branch (UserForm record)))
                  (dom/fieldset (Branch (UserForm record)))))]
    (e/for [[t tx] edits] ; one batch per form
      (prn 'batch (e/Count edits) t tx)
      (case (e/server ; insecure by design - wide open crud endpoint
              (e/Offload #(do (Thread/sleep 500) (d/transact! !conn tx) ::ok)))
        ::ok (t)))))