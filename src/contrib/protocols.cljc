(ns contrib.protocols
  (:require
    [clojure.string :as str]
    [contrib.debug :as dbg]))

(defprotocol Identifiable (-identify [o]))

(defn identify [o] (str (-identify o)))

#?(:clj
   (extend-protocol Identifiable
     clojure.lang.Namespace
     (-identify [ns] (ns-name ns))
     Object
     (-identify [_])
     nil
     (-identify [_])))
