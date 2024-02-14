(ns fiddles
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle :as hf]
            electric-fiddle.main
            ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
            ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
            #?@(:default #=(config/loaded-fiddles))))

(e/def fiddles (merge #?@(:default #=(config/loaded-fiddles-entrypoints)))) ; ensures clj and cljs stays in sync

(e/defn FiddleMain [ring-req]
  (e/client
    (binding [hf/pages fiddles]
      (e/server (electric-fiddle.main/Main. ring-req)))))

