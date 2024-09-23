(ns electric-tutorial.forms5
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer [InputSubmitCreate!]]))

(e/defn Forms5 []
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (InputSubmitCreate! :placeholder "Send")
                (InputSubmitCreate! :placeholder "Send"))]
    (dom/code (dom/text (pr-str v)))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t)))))