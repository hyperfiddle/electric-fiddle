(ns electric-tutorial.forms4
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.forms0 :as forms :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer
             [Input! Checkbox! ; can we reuse these?
              ]]
            [electric-tutorial.forms3 :refer [cmds->tx #?(:clj !conn)]]))

(e/defn UserForm [{:keys [db/id user/str1 user/num1 user/bool1]}]
  (dom/dl
    (e/amb ; inline commit/discard
      (dom/dt (dom/text (name :user/str1)))
      (dom/dd (Stage
                (Field id :user/str1
                  (Input! str1)))) ; todo inline stage capability into input

      (dom/dt (dom/text (name :user/num1)))
      (dom/dd (Stage
                (Field id :user/num1
                  (e/for [[t v] (Input! num1 :type "number")]
                    [t (parse-long v)]))))

      (dom/dt (dom/text (name :user/bool1)))
      (dom/dd (Stage
                (Field id :user/bool1
                  (Checkbox! bool1)))))))

(e/defn Forms4 []
  (let [db (e/server (e/watch !conn))
        edits (e/for [id (e/amb 42 42)]
                (dom/fieldset
                  (let [record (e/server (d/pull db [:db/id :user/str1
                                                    :user/bool1 :user/num1] id))]
                    #_(Stage) ; NO stage at form level
                    (UserForm record))))]
    (prn 'edits (e/Count edits))
    (e/for [[t cmds] edits]
      (prn 'edit t cmds)
      (let [res (e/server (prn 'cmds cmds)
                  (let [tx (cmds->tx cmds)]
                    (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                  #_(assert false "die") ; random failure
                                  (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                  (catch Exception e [::fail (str e)])))))
            [status err] res]
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err))))))