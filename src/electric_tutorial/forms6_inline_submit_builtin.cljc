(ns electric-tutorial.forms6-inline-submit-builtin
  #?(:cljs (:require-macros electric-tutorial.forms6-inline-submit-builtin))
  (:require [contrib.str :refer [pprint-str]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Field Service]]
            [hyperfiddle.input-zoo0 :as z :refer [Checkbox! Input! Button!]]
            [electric-tutorial.forms3-crud :refer [#?(:clj expand-tx-effects) #?(:clj !conn)]]))

#?(:cljs
   (defn blur-active-form-input! [form]
     (when-let [focused-input (.-activeElement js/document)]
       (when (.contains form focused-input)
         (.blur focused-input)))))

(e/defn FormSubmit!
  [directive & {:keys [disabled token show-button label] :as props}]
  (e/client
    (e/amb
      #_(e/When show-button) (Button! directive :disabled disabled :label label)
      (let [[t err] (e/RetryToken (dom/On "submit" #(do (.preventDefault %) (.stopPropagation %)
                                                      (when-not disabled %)) nil))]
        (prn 'Submit! t err)
        (when (some? err) (dom/props {:aria-invalid true})) ; glitch
        (if t [t directive] (e/amb))))))

(e/defn FormDiscard! ; dom/node must be a form
  [directive & {:keys [disabled token show-button label] :as props}]
  (e/client
    (dom/On "keyup" #(when (= "Escape" (.-key %)) (.stopPropagation %) (.reset dom/node) nil) nil) ; proxy esc to reset
    (e/amb
      #_(e/When show-button) (Button! directive :disabled disabled :label label)

      (let [e (dom/On "reset" #(do (.log js/console %) (.preventDefault %)
                                 (blur-active-form-input! (.-target %)) %) nil)
            [t err] (e/RetryToken e)]
        (prn 'Discard! t err)
        (if t [t directive] (e/amb))))))

;; Same as cqrs0/Stage, but also handles form submit and reset events
(e/defn Form* "implies an explicit txn monoid" ; doesn't work on raw values
  ([edits] (Form* edits :merge-cmds hyperfiddle.cqrs0/merge-cmds))
  ([[ts txs :as edits] ; amb destructure
    & {:keys [debug merge-cmds ::z/commit ::z/discard] :as props
       :or {merge-cmds hyperfiddle.cqrs0/merge-cmds
            debug false}}]
   (e/client
     (let [form (apply merge-cmds (e/as-vec txs))
           form-t (let [ts (e/as-vec ts)]
                    (fn token
                      ([] (doseq [t ts] (t)))
                      #_([err] (doseq [t ts] (t err))))) ; we could route errors to dirty fields, but it clears dirty state
           dirty-count (e/Count edits)
           clean? (zero? dirty-count)
           [btn-ts _ :as btns]
           (e/with-cycle* first [btns (e/amb)]
             (let [busy? (e/Some? btns)]
               (e/amb     ; todo progress
                 (FormSubmit! ::commit :disabled (or busy? clean?) :label (if busy? "commit" "commit") :show-button true)
                 (FormDiscard! ::discard :disabled clean? :label (if busy? "cancel" "discard") :show-button true)
                 (e/When debug (dom/span (dom/text " " dirty-count " dirty"))))))]

       (e/amb
         (e/for [[btn-t cmd] btns]
           (case cmd ; does order of burning matter?
             ::discard (case ((fn [] (btn-ts) (form-t))) ; clear any in-flight commit yet outstanding
                         (if discard discard (e/amb))) ; clear edits, controlled form will reset
             ::commit [(fn token
                         ([] (form-t) (btn-t))
                         ([err] (btn-t err) #_(form-t err))) ; leave dirty fields dirty, activates retry button
                       (if commit (commit form) form)])) ; commit as atomic batch

         (e/When debug
           (dom/pre (dom/props {:style {:min-height "4em"}})
             (dom/text (pprint-str form :margin 80)))))))))

(defmacro Form [& body]
  `(dom/form ; for "reset" event
     (Form* ~@body)))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd (Form
                    (Field id :user/str1
                      (Input! str1))))

          (dom/dt (dom/text "num1"))
          (dom/dd (Form ; field stage
                    (Field id :user/num1
                      (e/for [[t v] (Input! num1 :type "number")]
                        [t (parse-long v)]))))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Form ; field stage
                    (Field id :user/bool1
                      (Checkbox! bool1))))))))) ; bundled commit/discard

(e/defn Forms6-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))]
    (Service (e/server (identity expand-tx-effects))
      (e/amb
        (UserForm db 42) #_(Stage :debug true) ; NO stage at form level
        (UserForm db 42) #_(Stage :debug true)))))