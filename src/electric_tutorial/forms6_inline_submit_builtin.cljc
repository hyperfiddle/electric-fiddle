(ns electric-tutorial.forms6-inline-submit-builtin
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as forms :refer [Field Stage]]
            [hyperfiddle.input-zoo0 :refer [InputSubmit! CheckboxSubmit!]]
            [electric-tutorial.forms3-crud :refer [cmds->tx #?(:clj !conn)]]))

(e/defn UserForm [{:keys [db/id user/str1 user/num1 user/bool1]}]
  (dom/dl
    (e/amb ; inline commit/discard
      (dom/dt (dom/text "str1"))
      (dom/dd (Field id :user/str1
                (InputSubmit! str1)))

      (dom/dt (dom/text "num1"))
      (dom/dd (Field id :user/num1
                (e/for [[t v] (InputSubmit! num1 :type "number")]
                  [t (parse-long v)])))

      (dom/dt (dom/text "bool1"))
      (dom/dd (Field id :user/bool1
                (CheckboxSubmit! bool1))))))

(e/defn Forms6-inline-submit-builtin []
  (let [db (e/server (e/watch !conn))
        edits (e/for [id (e/amb 42 42)]
                (dom/fieldset
                  (let [record (e/server (d/pull db [:db/id :user/str1
                                                     :user/bool1 :user/num1] id))]
                    #_(Stage (UserForm record) :debug true) ; NO stage at form level
                    (UserForm record))))]
    (prn 'edits (e/Count edits) #_(e/as-vec (second edits)))
    (e/for [[t cmds] (e/Filter some? edits)]
      (prn 'edit cmds)
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