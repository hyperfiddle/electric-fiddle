(ns dustingetz.identify)

(defprotocol Identifiable (-identify [o]))

(defn identify [?o] (when (satisfies? Identifiable ?o) (-identify ?o)))

(comment (identify nil) := nil)

#?(:clj
   (extend-protocol Identifiable
     clojure.lang.Namespace
     (-identify [ns] (ns-name ns))
     Object
     (-identify [_])
     nil
     (-identify [_])))
