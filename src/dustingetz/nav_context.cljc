(ns dustingetz.nav-context
  (:require [clojure.datafy]))

(defprotocol NavContext (-nav-context [o]))

(defn nav-context
  "Provide an opportunity to compute (meta (datafy x)) without touching all attributes.
  User implementation should respect (= (nav-context x) (meta (datafy x))).
  Use case: hf-pull produces navigable pulled maps, without touching all attributes."
  [x]
  (some-> (-nav-context x)
    (assoc :clojure.datafy/obj x)
    #?(:clj (assoc :clojure.datafy/class (-> x class .getName symbol))) ; https://github.com/clojure/clojurescript/blob/26dea31110502ae718399868ad7385ffb8efe6f6/src/main/cljs/clojure/datafy.cljs#L25-L27
    ))

(extend-protocol NavContext
  nil
  (-nav-context [_])

  #?(:clj Object, :cljs default)
  (-nav-context [_] {}))

(comment
  (nav-context nil) := nil
  (nav-context java.lang.String)
   := {:clojure.datafy/obj java.lang.String, :clojure.datafy/class java.lang.Class}
  )