(ns dustingetz.scratch
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [effects* Button! Service]]))


(e/defn Scratch []
  (Service (Button! ['hello 42] :label "X")))
