(ns dustingetz.datafy-clj
  (:require clojure.core.protocols))

(extend-protocol clojure.core.protocols/Datafiable
  clojure.lang.Var
  (datafy [x]
    {::deref (str @x)
     ::toSymbol (.toSymbol x)
     ::getTag (.getTag x)
     ::isMacro (.isMacro x)
     ::ns (.-ns x)
     ::class (class x) ; hack, todo better browser
     }))

(comment
  (def x #'dev/DevMain)
  (.-ns x))