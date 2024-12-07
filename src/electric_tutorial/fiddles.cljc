(ns electric-tutorial.fiddles
  (:require [dustingetz.explorer :refer [DirectoryExplorer]]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [electric-tutorial.tutorial :refer [Tutorial]]
            [hyperfiddle.electric3 :as e]))

(e/defn Fiddles []
  {'tutorial Tutorial
   `DirectoryExplorer DirectoryExplorer})

(e/defn ProdMain [ring-req]
  ; keep /tutorial/ in the URL
  (FiddleMain ring-req (Fiddles)
    :default '(tutorial)))
