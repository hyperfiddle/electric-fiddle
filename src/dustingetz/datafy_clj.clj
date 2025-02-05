(ns dustingetz.datafy-clj
  (:require [clojure.core.protocols :refer [Datafiable]]
            [dustingetz.identify :refer [Identifiable]]))

(ns-unmap *ns* 'nav)

(extend-type clojure.lang.Var
  Identifiable (-identify [^clojure.lang.Var x] (.toSymbol x))
  Datafiable
  (datafy [^clojure.lang.Var x]
    {::toSymbol (.toSymbol x)
     ::meta (meta x)
     ::getTag (.getTag x)
     ::isMacro (.isMacro x)
     #_#_::deref (str @x)}))

(comment
  (require '[clojure.datafy :refer [datafy nav]] '[dustingetz.identify :refer [identify]])
  (def x #'dev/DevMain)
  (identify x)
  (datafy x)
  (as-> x x
    (datafy x) (nav x ::meta (get x ::meta))))