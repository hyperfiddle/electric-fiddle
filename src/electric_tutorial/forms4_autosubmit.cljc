(ns electric-tutorial.forms4-autosubmit
  (:require [hyperfiddle.electric3 :as e]
            #_[hyperfiddle.electric-dom3 :as dom] ; no dom here!
            [hyperfiddle.cqrs0 :refer [Service]]
            [electric-tutorial.forms3-crud :refer
             [UserForm #?(:clj !conn) #?(:clj expand-tx-effects)]]))

(e/defn Forms4-autosubmit []
  (let [db (e/server (e/watch !conn))]
    (Service (e/server (identity expand-tx-effects))
      (e/amb
        (UserForm db 42) #_(Stage :debug true) ; no stage
        (UserForm db 42) #_(Stage :debug true)))))