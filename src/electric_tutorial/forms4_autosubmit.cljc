(ns electric-tutorial.forms4-autosubmit
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            #_[hyperfiddle.electric-dom3 :as dom] ; no dom here!
            [electric-tutorial.forms3-crud :refer
             [UserForm cmds->tx #?(:clj !conn)]]))

(e/defn Forms4-autosubmit []
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true) ; no stage
                (UserForm db 42) #_(Stage :debug true))]
    (prn (e/Count edits) 'edits)
    (e/for [[t cmds] edits]
      (prn 'edit cmds)
      (let [res (e/server (prn 'cmds cmds)
                  (let [tx (cmds->tx cmds)]
                    (e/Offload #(try (prn 'tx tx) (Thread/sleep 500)
                                  (assert false "die")
                                  (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                  (catch Throwable e [::fail ::rejected])))))
            [status err] res]
        (cond
          (= status ::ok) (t)
          (= status ::fail) (t err))))))