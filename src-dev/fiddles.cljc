(ns fiddles
  (:require [electric-fiddle.fiddle-index :refer [FiddlePage pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            ;; Inject fiddle namespaces, keeping clj and cljs builds in sync
            ;; :clj doesn't have #@ (read splicing), so we resort to #?@(:default ...)
            #?@(:default #=(config/loaded-fiddles))))

(e/defn FiddleMain [ring-req]
  (binding [e/http-request (e/server ring-req)
            dom/node js/document.body
            pages (merge #?@(:default ; ensure clj and cljs stay in sync
                             #=(config/loaded-fiddles-entrypoints)))]
    (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
      (r/router (r/HTML5-History)
        (FiddlePage)))))