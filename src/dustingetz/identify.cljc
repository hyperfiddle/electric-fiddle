(ns dustingetz.identify)

(defprotocol Identifiable
  :extend-via-metadata true
  (-identify [o]))

(defn identify [obj]
  "
Produces a symbolic identity for `obj`, or nil if `obj` does not explicitly
implement `Identifiable`. Serializable and uniquely resolvable symbolic
identities should be favored.
All values being self-identical, `(or (identify x) x)` always yields a valid
identifier, though it might not be serializable."
  (-identify obj))

(comment
  (identify (Object.)) := nil
  (let [obj (Object.)] (pr-str (or (identify obj) obj))) := "#object[java.lang.Object 0x75bf525d \"java.lang.Object@75bf525d\"]"
  (identify nil) := nil
  )

#?(:clj
   (extend-protocol Identifiable
     #?(:clj Object :cljs default)
     (-identify [_])
     nil
     (-identify [_])))
