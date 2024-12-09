(ns ui-demo.fiddles
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [ui-demo.scratch :refer [Scratch]]))

(e/defn Demo []
  (e/client
    (dom/h1 (dom/text "UI Demo"))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles [] ; FIXME DE - should be simplified to `(def fiddles {`a a})`
  {#_#_`Demo Demo
   `UiScratch Scratch})

;; Prod entrypoint, called by `prod.clj`
(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)]
      (dom/div ; mandatory wrapper div - https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (Demo))))))