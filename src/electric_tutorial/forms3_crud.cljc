(ns electric-tutorial.forms3-crud
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage]]
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

(defn cmds->tx [cmd-batch]
  (let [xf (mapcat (fn [cmd]
                     (match (doto cmd #_(prn 'cmd))
                       ; wide open crud endpoint (on purpose)
                       [::cqrs/update e a v] [{:db/id e a v}]
                       nil nil)))]
    (into [] xf cmd-batch)))

(tests
  (cmds->tx [[::cqrs/update 1 :a 1]]) := [{:db/id 1, :a 1}]
  (cmds->tx [[::cqrs/update 1 :a 1] [::cqrs/update 1 :b 2]])
  := [{:db/id 1, :a 1} {:db/id 1, :b 2}]
  (cmds->tx [[::cqrs/update 1 :a 1] nil]) := [{:db/id 1, :a 1}]
  (cmds->tx [[::yo :mom]]) :throws #?(:clj IllegalArgumentException :cljs :default)
  (cmds->tx [nil]) := []
  (cmds->tx nil) := [])

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

(e/defn Service [edits]
  (prn (e/Count edits) 'edits)
  (e/for [[t cmds] edits]
    (let [res (e/server (prn 'cmds cmds)
                (let [tx (cmds->tx cmds)] ; secure cmd interpretation
                  (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                (assert false "die") ; random failure
                                (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                (catch Throwable e [::fail ::rejected])))))
          [status err] res]
      (cond
        (= status ::ok) (t)
        (= status ::fail) (t err))))) ; feed error back into control for retry affordance

(e/defn Forms3-crud [] ; async, transactional, entity backed, never backpressure
  (let [db (e/server (e/watch !conn))
        edits (e/amb ; concurrent form submits, one per form
                (Stage (UserForm db 42) :debug true) ; buffer and batch edits into an atomic form
                (Stage (UserForm db 42) :debug true))]
    (Service edits)))