(ns fiddles
  (:require [electric-fiddle.fiddle-index :refer [FiddlePage pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [electric-fiddle.fiddle-index :refer []]
            ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
            ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
            #?@(:default #=(config/loaded-fiddles))))

;; FIXME DE - should be simplified to `(def fiddles (merge …))`
(e/defn Fiddles [] (merge #?@(:default #=(config/loaded-fiddles-entrypoints)))) ; ensures clj and cljs stays in sync

(e/defn FiddleMain [ring-req]
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body
            pages (Fiddles)]
    (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (r/router (r/HTML5-History)
        (FiddlePage)))))