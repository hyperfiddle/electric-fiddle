(ns fiddles
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle :as hf]
            electric-fiddle.main
            ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
            ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
            #?@(:default #=(config/loaded-fiddles))
            ))

(def fiddles (merge #?@(:default #=(config/loaded-fiddles-entrypoints)))) ; ensures clj and cljs stays in sync

(e/defn FiddleMain [ring-req]
  (binding [hf/pages fiddles]
    ($ electric-fiddle.main/Main ring-req)))

