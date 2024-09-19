(ns electric-tutorial.forms
  #?(:cljs (:require-macros electric-tutorial.forms))
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.forms0 :as forms :refer [Field Branch]]
            [electric-tutorial.input-zoo :refer [Input! Checkbox!]]))

(e/defn UserForm [{:keys [db/id user/x-bool1 user/x-str1 user/x-num1]}]
  (dom/dl
    (e/amb
      (Field id :user/x-str1 (Input! x-str1))
      (Field id :user/x-num1 (e/for [[t v] (Input! x-num1 :type "number")]
                               [t (parse-long v)]))
      (Field id :user/x-bool1 (Checkbox! x-bool1)))))

(defn cmd->tx [cmd]
  (match (doto cmd #_(prn 'cmd))
    [::forms/update e a v] [{:db/id e a v}] ; wide open crud endpoint (on purpose)
    :else (prn 'miss cmd)))

(defn cmds->tx [cmd-batch] (into [] (mapcat cmd->tx) cmd-batch))

(comment
  (cmds->tx [[::forms/update 1 :a 1]]) := [{:db/id 1, :a 1}]
  (cmds->tx [[::forms/update 1 :a 1]
            [::forms/update 1 :b 2]]) := [{:db/id 1, :a 1} {:db/id 1, :b 2}]
  (cmds->tx [[::forms/update 1 :a 1] nil]) := [{:db/id 1, :a 1}]
  (cmds->tx [nil]) := []
  (cmds->tx nil) := [])

#?(:clj (defonce !conn (doto (d/create-conn {})
                         (d/transact! [{:db/id 42 :user/x-bool1 true :user/x-str1 "one" :user/x-num1 1}]))))

(e/defn Forms []
  (let [db (e/server (e/watch !conn))
        record (e/server (d/pull db [:db/id :user/x-bool1 :user/x-str1 :user/x-num1] 42))
        edits (dom/div
                (e/amb
                  (dom/fieldset (Branch (UserForm record)))
                  (dom/fieldset (Branch (UserForm record)))))]
    (prn 'edits (e/Count edits))
    (e/for [[t cmds] edits] ; one batch per form
      (prn 'edit t cmds)
      (let [res (e/server (prn 'cmds cmds)
                  (let [tx (cmds->tx cmds)] ; secure cmd interpretation
                    (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                  #_(assert false "die") ; random failure
                                  (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                  (catch Exception e [::fail (str e)]))))) ; todo datafy err
            [status err] res]
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err))))))