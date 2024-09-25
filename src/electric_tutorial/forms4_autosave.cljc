(ns electric-tutorial.forms4-autosave
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3-crud :refer
             [UserForm cmds->tx #?(:clj !conn)]]))

(e/defn Forms4-autosave [] ; async, transactional, entity backed, never backpressure
  (let [db (e/server (e/watch !conn))
        edits (e/for [id (e/amb 42 42)] ; two forms submitting edits concurrently
                (dom/fieldset
                  (let [record (e/server (d/pull db [:db/id :user/str1
                                                     :user/num1 :user/bool1] id))
                        field-edits (UserForm record) ; concurrent
                        form-edits field-edits #_(Stage field-edits :debug true)] ; buffered and batched
                    form-edits)))]
    (prn 'edits (e/Count edits))
    (e/for [[t cmds] (e/Filter some? edits)] ; concurrent batches, one batch per form
      (prn 'edit t cmds)
      (let [res (e/server (prn 'cmds cmds)
                  (let [tx (cmds->tx cmds)] ; secure cmd interpretation
                    (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                  (assert false "die") ; random failure
                                  (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                  (catch Throwable e [::fail ::rejected])))))
            [status err] res]
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err)))))) ; feed error back into control for retry affordance

(comment
  (cmds->tx [[::cqrs/update 1 :a 1]]) := [{:db/id 1, :a 1}]
  (cmds->tx [[::cqrs/update 1 :a 1] [::cqrs/update 1 :b 2]]) :=
  [{:db/id 1, :a 1}        {:db/id 1, :b 2}]
  (cmds->tx [[::cqrs/update 1 :a 1] nil]) := [{:db/id 1, :a 1}]
  (cmds->tx [nil]) := []
  (cmds->tx nil) := [])