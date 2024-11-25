(ns fiddles
  (:require [electric-fiddle.fiddle-index :refer [FiddleIndex Entrypoint pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
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
        #_(dom/pre (dom/text (pr-str r/route)))
        (let [[fiddle & _] r/route]
          (if-not fiddle (r/ReplaceState! ['. `(FiddleIndex)])
            (do
              (set! (.-title js/document) (str (some-> fiddle name (str " – ")) "Electric Fiddle"))
              (case fiddle
                `FiddleIndex (FiddleIndex)
                (r/pop
                  (Entrypoint fiddle))))))))))