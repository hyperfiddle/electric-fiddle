(ns hello-fiddle.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

(e/defn Hello []
  (e/client
    (dom/h1 (dom/text "Hello world"))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles [] {`Hello Hello}) ; FIXME DE - should be simplified to `(def fiddles {`a a})`

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  #_(e/server (binding [e/http-request ring-request])) ; make ring request available through the app
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      ($ Hello))))
