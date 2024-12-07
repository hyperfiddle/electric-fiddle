(ns load-prod-fiddles!
  (:require
    #?(:clj prod-fiddle-config)
    ; cljs only, clj is in uberjar and dyna-loaded
    #?(:cljs #=(clojure.core/identity prod-fiddle-config/*comptime-prod-fiddle-ns*))))