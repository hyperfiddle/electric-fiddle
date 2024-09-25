(ns electric-tutorial.forms3-crud
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]))

(e/defn UserForm [{:keys [db/id user/str1 user/num1 user/bool1]}]
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
                (Checkbox! bool1))))))

(defn cmd->tx [cmd]
  (match (doto cmd #_(prn 'cmd))
    [::cqrs/update e a v] [{:db/id e a v}] ; wide open crud endpoint (on purpose)
    :else (prn 'miss cmd)))

(defn cmds->tx [cmd-batch] (into [] (mapcat cmd->tx) cmd-batch))

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/str1 "one"
                                        :user/num1 1 :user/bool1 true}]))))

(e/defn Forms3-crud [] ; async, transactional, entity backed, never backpressure
  (let [db (e/server (e/watch !conn))
        edits (e/for [id (e/amb 42 42)] ; two forms submitting edits concurrently
                (dom/fieldset
                  (let [record (e/server (d/pull db [:db/id :user/str1
                                                     :user/num1 :user/bool1] id))
                        field-edits (UserForm record) ; concurrent
                        form-edits (Stage field-edits :debug true)] ; buffered and batched
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