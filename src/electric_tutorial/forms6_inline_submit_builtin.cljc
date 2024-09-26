(ns electric-tutorial.forms6-inline-submit-builtin
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Stage Field]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox! Button!]]
            [electric-tutorial.forms3-crud :refer [Service #?(:clj !conn)]]))

#?(:cljs
   (defn blur-active-form-input! [form]
     (when-let [focused-input (.-activeElement js/document)]
       (when (.contains form focused-input)
         (.blur focused-input)))))

;; Same as cqrs0/Stage, but also handles form submit and reset events
(e/defn FormStage "implies an explicit txn monoid" ; doesn't work on raw values
  ([edits] (FormStage edits :merge-cmds hyperfiddle.cqrs0/merge-cmds))
  ([[ts txs :as edits] ; amb destructure
    & {:keys [debug merge-cmds]
       :or {merge-cmds hyperfiddle.cqrs0/merge-cmds
            debug false}}]
   (e/client
     (let [batch (apply merge-cmds (e/as-vec txs))
           reset (let [ts (e/as-vec ts)]
                   (fn token
                     ([] (doseq [t ts] (t)))
                     #_([err] (doseq [t ts] (t err ::keep))))) ; we could route errors to dirty fields, but it clears dirty state
           n (e/Count edits)
           [us _ :as btns] (e/amb     ; todo progress
                             (let [[t err] (e/RetryToken (dom/On "submit" #(do (.preventDefault %) (.stopPropagation %) (when-not (zero? n) %)) nil))]
                               (prn 'Submit! t err)
                               (when (some? err) (dom/props {:aria-invalid true})) ; bug - error not removed (glitch)
                               (if t [t ::commit] (e/amb)))
                             (let [[t err] (e/RetryToken (do
                                                           (dom/On "keyup" #(when (= "Escape" (.-key %)) (.stopPropagation %) (.reset dom/node)))
                                                           (dom/On "reset" #(do (.log js/console %) (.preventDefault %) (blur-active-form-input! (.-target %)) %) nil)))]
                               (prn 'Discard! t err)
                               (if t [t ::discard] (e/amb)))
                             (Button! ::commit :disabled (zero? n) :label "commit" :type :button)
                             (Button! ::discard :disabled (zero? n) :label "discard" :type :button)
                             #_(e/When debug (dom/span (dom/text " " n " dirty"))))]
       n ; force edits count
       (e/amb
         (e/for [[u cmd] btns]
           (case cmd ; does order of burning matter?
             ::discard (case ((fn [] (us) (reset))) ; clear any in-flight commit yet outstanding
                         (e/amb)) ; clear edits, controlled form will reset
             ::commit [(fn token
                         ([] (reset) (u))
                         ([err] (u err) #_(reset err))) ; leave dirty fields dirty, activates retry button
                       batch])) ; commit as atomic batch

         (e/When debug
           (dom/pre (dom/props {:style {:min-height "4em"}})
                    (dom/text (pprint-str batch :margin 80)))))))))

(e/defn Form* [Body]
  (dom/form
    (FormStage (Body))))

(defmacro Form [& body]
  `(Form* (e/fn [] ~@body)))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb
          (dom/dt (dom/text "str1"))
          (dom/dd
            (Form
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
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true) ; NO stage at form level
                #_(UserForm db 42) #_(Stage :debug true))]
    (Service edits)))
