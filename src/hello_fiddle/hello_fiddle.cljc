(ns hello-fiddle.hello-fiddle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric-forms5 :as forms]
            [dustingetz.offload-ui :refer [Initialized OffloadUI]]))

(declare css)

(e/defn Scratch []
  (e/client
    (dom/h1 (dom/text "Hello world"))
    (dom/style (dom/text css))
    (let [input (forms/Input* 0 :type :number)]
      (dom/pre
        (dom/text "value is " (Initialized (e/server (OffloadUI 'task-label #(do (Thread/sleep 2000) input))) "..."))))))

(def css
  (str dustingetz.offload-ui/css
    "

pre[data-timing-label]{
  display: block;
  width: fit-content;
  margin: 2rem 0;
}
"))


;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles []
  {`Scratch Scratch})

;; Prod entrypoint, called by `prod.clj`
(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)]
      (dom/div ; mandatory wrapper div - https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (Scratch))))))