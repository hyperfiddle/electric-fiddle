(ns electric-tutorial.fiddles
  (:require [electric-fiddle.fiddle-index :refer [FiddlePage pages]]
            [electric-tutorial.tutorial :refer [Tutorial]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/defn Fiddles []
  {#_`Tutorial 'tutorial Tutorial})

(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)
              pages (Fiddles)]
      (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          #_(Tutorial) ; we're going to deploy multiple fiddles to electric.hf.net
          (FiddlePage ; keep /tutorial/ in the URL
            :default '(tutorial)))))))
