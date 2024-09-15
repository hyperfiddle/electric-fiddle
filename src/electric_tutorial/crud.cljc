(ns electric-tutorial.crud
  (:require [contrib.str :refer [pprint-str]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

; forms + collection

(e/defn Crud [])