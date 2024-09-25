(ns electric-tutorial.forms5-inline-submit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer [Input! Checkbox!]]
            [electric-tutorial.forms3-crud :refer
             [cmds->tx #?(:clj !conn)]]))

(e/defn UserForm [{:keys [db/id user/str1 user/num1 user/bool1]}]
  (dom/dl
    (e/amb
      (dom/dt (dom/text "str1"))
      (dom/dd (Stage
                (Field id :user/str1
                  (Input! str1))))

      (dom/dt (dom/text "num1"))
      (dom/dd (Stage
                (Field id :user/num1
                  (e/for [[t v] (Input! num1 :type "number")]
                    [t (parse-long v)]))))

      (dom/dt (dom/text "bool1"))
      (dom/dd (Stage
                (Field id :user/bool1
                  (Checkbox! bool1)))))))

(e/defn Forms5-inline-submit []
  (let [db (e/server (e/watch !conn))
        edits (e/for [id (e/amb 42 42)]
                (dom/fieldset
                  (let [record (e/server (d/pull db [:db/id :user/str1
                                                     :user/num1 :user/bool1] id))
                        field-edits (UserForm record)
                        form-edits field-edits #_(Stage field-edits :debug true)]
                    form-edits)))]
    (prn 'edits (e/Count edits))
    (e/for [[t cmds] (e/Filter some? edits)]
      (prn 'edit t cmds)
      (let [res (e/server (prn 'cmds cmds)
                  (let [tx (cmds->tx cmds)]
                    (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                  (assert false "die") ; random failure
                                  (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                  (catch Throwable e [::fail ::rejected])))))
            [status err] res]
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err))))))