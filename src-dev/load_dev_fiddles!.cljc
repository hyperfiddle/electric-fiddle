(ns load-dev-fiddles!
  ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
  ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
  (:require #?@(:default #=(dev-fiddle-config/dev-fiddle-namespaces))))