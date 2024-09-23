(ns electric-tutorial.todos2
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.input-zoo0 :refer []]
            [electric-tutorial.todos :refer [#?(:clj !conn)]]))

(e/defn Todos2 []
  (let [db (e/server (e/watch !conn))]
    ))