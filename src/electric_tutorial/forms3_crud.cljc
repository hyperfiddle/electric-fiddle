(ns electric-tutorial.forms3-crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage Service]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [hyperfiddle.rcf :refer [tests]]))

(e/defn UserForm [db id]
  (dom/fieldset
    (let [{:keys [user/str1 user/num1 user/bool1]}
          (e/server (d/pull db [:user/str1 :user/num1 :user/bool1] id))]
      (dom/dl
        (e/amb ; 3 fields submitting edits concurrently
          (dom/dt (dom/text "str1"))
          (dom/dd (Field id :user/str1
                    (Input! str1)))

          (dom/dt (dom/text "num1"))
          (dom/dd (Field id :user/num1
                    (e/for [[t v] (Input! num1 :type "number")]
                      [t (parse-long v)])))

          (dom/dt (dom/text "bool1"))
          (dom/dd (Field id :user/bool1
                    (Checkbox! bool1))))))))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

#?(:clj (defn slow-transact! [tx]
          (prn 'tx tx) (Thread/sleep 500) (assert false "die")
          (d/transact! !conn tx)))

(defn expand-tx-effects
  "expand atomic form paylod `cmd-batch` into a batch of ideally one effect,
but maybe there are non-atomic sibling effects to i.e. send emails"
  [cmd-batch]
  (->> (group-by first cmd-batch)
    (mapcat (fn [[cmd all-cmd-instances]]
              (letfn [(inline-edit->tx [[_ e a v]] [{:db/id e a v}])] ; wide open crud endpoint (on purpose)
                (case cmd
                  ::cqrs/update ; merge all the crud stuff into 1 tx
                  (let [tx (into [] (mapcat inline-edit->tx) all-cmd-instances)]
                    [[#?(:clj slow-transact! :cljs nil) tx]]) ; transparent effect type for tests (closures are opaque)
                  nil nil))))))

(tests
  (expand-tx-effects [[::cqrs/update 1 :a 1] [::cqrs/update 1 :b 2]])
  := [[#?(:clj slow-transact! :cljs nil) [{:db/id 1, :a 1} {:db/id 1, :b 2}]]]
  (expand-tx-effects [[::yo :mom]]) :throws #?(:clj IllegalArgumentException :cljs :default)
  (expand-tx-effects [[::cqrs/update 1 :a 1] nil]) := [[#?(:clj slow-transact! :cljs nil) [{:db/id 1, :a 1}]]]
  (expand-tx-effects [nil]) := []
  (expand-tx-effects nil) := [])

(e/defn Forms3-crud [] ; async, transactional, entity backed, never backpressure
  (let [db (e/server (e/watch !conn))
        txns (e/amb ; concurrent form submits, one per form
               (Stage (UserForm db 42) :debug true) ; buffer and batch edits into an atomic form
               (Stage (UserForm db 42) :debug true))]
    (Service (e/server (identity expand-tx-effects))
      txns)))