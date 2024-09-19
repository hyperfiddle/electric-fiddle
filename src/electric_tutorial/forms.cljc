(ns electric-tutorial.forms
  #?(:cljs (:require-macros electric-tutorial.forms))
  (:require [clojure.core.match :refer [match]]
            [contrib.str :refer [pprint-str]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.input-zoo :refer [Input! Checkbox!]]))

; commit/discard with staging area
; inputs - emit as you type - with a token
; stage - monitor edits and accumulate them
; button - batch all edits into single token, chained with upstream tokens

(e/defn Field* [e a Control]
  (e/amb
    (dom/dt (dom/text (name a)))
    (dom/dd (e/for [[t v] (Control)]
              [t [{:db/id e a v}]])))) ; insecure by design - txns from client authority

(defmacro Field [e a body] `(Field* ~e ~a (e/fn [] ~body)))

(e/defn UserForm [{:keys [db/id user/x-bool user/x-str1 user/x-num1]}]
  (dom/dl
    (e/amb
      (Field id :user/x-str1 (Input! x-str1))
      (Field id :user/x-num1 (e/for [[t v] (Input! x-num1 :type "number")]
                               [t (parse-long v)]))
      (Field id :user/x-bool (Checkbox! x-bool)))))

(defn merge-tx [& txs] (vec (apply concat txs)))

(e/defn Button! [directive & {:keys [label disabled id error] :as props
                              :or {id (random-uuid)}}]
  (dom/button (dom/text label)
    (dom/props (-> props (dissoc :label :disabled) (assoc :id id)))
    (let [t (e/Token (dom/On "click"))]
      (dom/props {:disabled (or disabled (some? t)) ; todo compile kw args
                  :aria-invalid (some? error)})
      (when (some? error)
        (dom/span (dom/text error) (dom/props {:class "hyperfiddle-error"})))
      (e/When (some? t) [t directive]))))

(e/defn Branch [[ts txs :as edits]] ; amb destructure
  (let [batch (apply merge-tx (e/as-vec txs))
        reset (let [ts (e/as-vec ts)] #(doseq [t ts] (t)))
        n (e/Count edits)
        !commit-err (atom nil) commit-err (e/watch !commit-err)
        [ts _ :as btns] (e/amb
                          (Button! ::commit :label "commit" :disabled (zero? n) :error commit-err) ; todo progress
                          (Button! ::discard :label "discard" :disabled (zero? n))
                          (dom/span (dom/text " " n " dirty")))]
    (e/amb
      (e/for [[t cmd] btns]
        (case cmd
          ::discard (case (ts (reset)) ; clear any in-flight commit yet outstanding
                      (e/amb)) ; clear edits, controlled form will reset
          ::commit [(fn token
                      ([] (t (reset)))
                      ([err] (reset! !commit-err err) (t)))
                    batch])) ; commit as atomic batch

      (dom/pre (dom/props {:style {:min-height "4em"}})
        (dom/text (pprint-str batch :margin 40))))))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/x-bool true :user/x-str1 "one" :user/x-num1 1}]))))

(e/defn Forms []
  (let [db (e/server (e/watch !conn))
        record (e/server (d/pull db [:db/id :user/x-bool :user/x-str1 :user/x-num1] 42))
        edits (dom/div
                (e/amb
                  (dom/fieldset (Branch (UserForm record)))
                  (dom/fieldset (Branch (UserForm record)))))]
    (e/for [[t tx] edits] ; one batch per form
      (prn 'batch (e/Count edits) t tx) ; fixme e/drain glitch
      (let [[status err] (e/server ; insecure by design - wide open crud endpoint
                           (e/Offload #(try ; random failure
                                         (Thread/sleep 500)
                                         (assert false "die")
                                         (d/transact! !conn tx) [::ok] ; insecure
                                         (catch Exception e [::fail (str e)]))))] ; todo datafy
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err))))))