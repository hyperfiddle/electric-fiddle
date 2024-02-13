(ns fiddles
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle :as hf]
            electric-fiddle.main
            ;; Injects loaded fiddles namespaces, ensures clj and cljs stays in sync
            ;; - `#?@` will splice a list in-place
            ;; - `:default` means "for all languages" (e.g. both :clj and :cljs)
            ;;   - clojure doesn't have #@ (aka. read splicing), so we resort to #?@(:default ...)
            ;; - `#=` evals the next form at read time
            #?@(:default #=(fiddle-manager/loaded-fiddles))))

(e/def fiddles (merge #?@(:default #=(fiddle-manager/loaded-fiddles-entrypoints)))) ; ensures clj and cljs stays in sync

(e/defn FiddleMain [ring-req]
  (e/client
    (binding [hf/pages fiddles]
      (e/server (electric-fiddle.main/Main. ring-req)))))

