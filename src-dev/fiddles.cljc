(ns fiddles
  (:require [electric-fiddle.fiddle-index :refer [pages]]
            [electric-fiddle.main :refer [Main]]
            ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
            ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
            #?@(:default #=(config/loaded-fiddles))
            [hyperfiddle.electric3 :as e]))

;; FIXME DE - should be simplified to `(def fiddles (merge …))`
(e/defn Fiddles [] (merge #?@(:default #=(config/loaded-fiddles-entrypoints)))) ; ensures clj and cljs stays in sync

(e/defn FiddleMain [ring-req]
  (binding [pages (Fiddles)]
    (Main ring-req)))

