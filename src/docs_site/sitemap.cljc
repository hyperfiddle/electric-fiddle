(ns docs-site.sitemap
  (:require [hyperfiddle.electric3 :as e]
            [electric-fiddle.fiddle-index :refer [FiddleMain]]
            [electric-essay.tutorial-app :refer [Tutorial]]

            datomic-browser.mbrainz-browser
            [electric-tutorial.tutorial-sitemap :refer [TutorialFiddles tutorial-sitemap]]
            staffly.staffly
            ))

(def demo-sitemap
  [["Demos"
    ['explorer]]])

(e/defn Fiddles []
  (merge
    {'tutorial (e/Partial Tutorial tutorial-sitemap "src/electric_tutorial/")
     #_#_'demo (e/Partial Tutorial demo-sitemap "src/docs_site/demos/")}
    (TutorialFiddles)
    (datomic-browser.mbrainz-browser/Fiddles)
    (staffly.staffly/Fiddles)))

(e/defn ProdMain [ring-req]
  ; keep /tutorial/ in the URL
  (FiddleMain ring-req (Fiddles)
    :default '(tutorial)))