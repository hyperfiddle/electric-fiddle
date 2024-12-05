(ns optimistic-todomvc.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [optimistic-todomvc.optimistic-todomvc]))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles [] {`Optimistic-TodoMVC optimistic-todomvc.optimistic-todomvc/OptimisticTodoMVC}) ; FIXME DE - should be simplified to `(def fiddles {`a a})`

;; Prod entrypoint, called by `prod.clj`
(e/defn ProdMain [ring-request]
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      ($ optimistic-todomvc.optimistic-todomvc/OptimisticTodoMVC))))
