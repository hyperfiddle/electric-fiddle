(ns dustingetz.datafy-clj
  (:require [clojure.core.protocols :refer [datafy nav]]))

(extend-protocol clojure.core.protocols/Datafiable
  clojure.lang.Var
  (datafy [x]
    {::toSymbol (.toSymbol x)
     ::meta (meta x)
     ::getTag (.getTag x)
     ::isMacro (.isMacro x)
     #_#_::deref (str @x)}))

(comment
  (def x #'dev/DevMain)
  (clojure.datafy/datafy x)
  (as-> x x
    (clojure.datafy/datafy x)
    (nav x ::src (get x ::src))))