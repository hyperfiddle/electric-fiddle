(ns electric-tutorial.fiddles
  (:require [dustingetz.explorer :refer [DirectoryExplorer]]
            [electric-fiddle.fiddle-index :refer [FiddleRoot]]
            [electric-tutorial.tutorial :refer [Tutorial]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(e/defn Fiddles []
  {'tutorial Tutorial
   `DirectoryExplorer DirectoryExplorer})

(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)]
      (dom/div ; mandatory wrapper div https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (FiddleRoot ; keep /tutorial/ in the URL
            (Fiddles)
            :default '(tutorial)))))))
