(ns load-dev-fiddles!
  ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
  ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
  (:require
    #?(:clj dev-fiddle-config)
    #?@(:default #=(dev-fiddle-config/comptime-dev-fiddle-namespaces))))