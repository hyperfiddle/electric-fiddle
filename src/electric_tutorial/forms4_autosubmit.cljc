(ns electric-tutorial.forms4-autosubmit
  (:require [hyperfiddle.electric3 :as e]
            #_[hyperfiddle.electric-dom3 :as dom] ; no dom here!
            [electric-tutorial.forms3-crud :refer
             [Service UserForm #?(:clj !conn)]]))

(e/defn Forms4-autosubmit []
  (let [db (e/server (e/watch !conn))
        edits (e/amb
                (UserForm db 42) #_(Stage :debug true) ; no stage
                (UserForm db 42) #_(Stage :debug true))]
    (Service edits)))