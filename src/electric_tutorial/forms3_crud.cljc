(ns electric-tutorial.forms3-crud
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            #_[hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]
            [hyperfiddle.cqrs0 :as cqrs]
            [electric-tutorial.forms3a-form :refer [Forms3a-form]]
            [electric-tutorial.forms3b-inline-submit :refer [Forms3b-inline-submit]]
            [electric-tutorial.forms3c-inline-submit-builtin :refer [Forms3c-inline-submit-builtin]]
            [electric-tutorial.forms3d-autosubmit :refer [Forms3d-autosubmit]]))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

#?(:clj (defn slow-transact! [tx] (prn 'tx tx)
          (Thread/sleep 500) (assert false "die") (d/transact! !conn tx)))

(e/defn Forms3-crud []
  (Forms3a-form)
  (Forms3b-inline-submit)
  (Forms3c-inline-submit-builtin)
  (Forms3d-autosubmit))




(defn expand-tx-effects [form]
  (->> (group-by first form)
    (mapcat (fn [[cmd all-cmd-instances]]
              (letfn [(inline-edit->tx [[_ e a v]] [{:db/id e a v}])] ; wide open crud endpoint (on purpose)
                (case cmd
                  :hyperfiddle.cqrs0/update ; merge all the crud stuff into 1 tx
                  (let [tx (into [] (mapcat inline-edit->tx) all-cmd-instances)]
                    [[#?(:clj slow-transact! :cljs nil) tx]]) ; transparent effect type for tests (closures are opaque)
                  nil nil ; flatten out
                  [[cmd all-cmd-instances]]))))))

#?(:clj
   (tests
     "translate UI form submits into secure effects"
     (expand-tx-effects [[::cqrs/update 42 :user/num1 9] ; a form is a list of commands
                         [::cqrs/update 42 :user/bool1 false]
                         [::send-email "alice@example.com" "hi"]])
     := [[slow-transact! ; secure effect
          [{:db/id 42, :user/num1 9} ; fields are batched
           {:db/id 42, :user/bool1 false}]]
         [::send-email [[:send-email "alice@example.com" "hi"]]]] ; unrecognized pass through

     ;(expand-tx-effects [[::yo :mom]]) :throws #?(:clj IllegalArgumentException :cljs :default)
     (expand-tx-effects [[::yo :mom]]) := [::yo [[::yo :mom]]] ; pass through unrecognized commands
     (expand-tx-effects [[::cqrs/update 1 :a 1] nil]) := [[slow-transact! [{:db/id 1, :a 1}]]]
     (expand-tx-effects [nil]) := []
     (expand-tx-effects nil) := []))

; deal with circular refs due to tutorial structure
#?(:clj (alter-var-root #'electric-tutorial.forms3a-form/!conn (constantly !conn)))
#?(:clj (alter-var-root #'electric-tutorial.forms3a-form/expand-tx-effects (constantly expand-tx-effects)))