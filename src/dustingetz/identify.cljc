(ns dustingetz.identify)

(defprotocol Identifiable
  :extend-via-metadata true
  (-identify [o]))

(defn identify [?o] (-identify ?o))

(comment (identify nil) := nil)

#?(:clj
   (extend-protocol Identifiable
     clojure.lang.Namespace
     (-identify [ns] (ns-name ns))
     Object
     (-identify [x] x) ; value is its own identity
     nil
     (-identify [_])))

#?(:cljs
   (extend-protocol Identifiable
     object
     (-identify [x] x) ; value is its own identity
     nil
     (-identify [_])))
