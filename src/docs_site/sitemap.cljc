(ns docs-site.sitemap
  (:require [hyperfiddle.electric3 :as e]
            [electric-fiddle.fiddle-index :refer [FiddleMain FiddleIndex]]
            [electric-essay.tutorial-app :refer [Tutorial]]
            [electric-essay.essay-app :refer [Essay]]
            [datomic-browser.datomic-browser3 :refer [Inject-datomic DatomicBrowser3]]
            #_[datomic-browser.datomic-browser4 :as db4]
            [docs-site.blog.index :refer [BlogIndex]]
            [dustingetz.unifhir1 :refer [Unifhir1]]
            [dustingetz.threaddump :refer [ThreadDump]]
            [docs-site.blog.threaddump1 :refer [ThreadDump1]]
            [docs-site.blog.threaddump2 :refer [ThreadDump2]]
            [docs-site.blog.threaddump3 :refer [ThreadDump3]]
            [docs-site.blog.waveform0 :refer [Waveform0]]
            [docs-site.tutorial-sitemap :refer [TutorialFiddles tutorial-sitemap]]
            [electric-tutorial.explorer :refer [DirectoryExplorer]]
            [hyperfiddle.router4 :as r]
            staffly.staffly
            #?(:clj dustingetz.mbrainz)))

(def blog-sitemap
  [["Blog"
    ['index
     'y20250106_threaddump_part1
     'y20250109_datafy
     'y20250112_data_browser
     'y20250123_waveform]]])

(e/defn BlogFiddles []
  {`BlogIndex (e/Partial BlogIndex blog-sitemap)
   `ThreadDump1 ThreadDump1
   `ThreadDump2 ThreadDump2
   `ThreadDump3 ThreadDump3
   `Waveform0 Waveform0})

(e/defn Utilities []
  {`ThreadDump ThreadDump})

(e/defn SecretDemos []
  (merge
    (staffly.staffly/Fiddles)
    ))

(e/defn ListedDemos []
  {'datomic-browser.mbrainz-browser/DatomicBrowser (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser3)]))
   `DatomicBrowser3 (Inject-datomic dustingetz.mbrainz/mbrainz-uri DatomicBrowser3)
   #_#_`db4/DatomicBrowser4 (db4/Inject-datomic dustingetz.mbrainz/mbrainz-uri db4/DatomicBrowser4)
   `Unifhir1 Unifhir1
   `ThreadDump3 ThreadDump3
   `DirectoryExplorer DirectoryExplorer})

(e/defn Fiddles []
  (merge
    {'tutorial (e/Partial Tutorial tutorial-sitemap "src/electric_tutorial/")
     'blog (e/Partial Essay blog-sitemap "src/docs_site/blog/")
     'demos (e/fn [] (binding [electric-fiddle.fiddle-index/pages (ListedDemos)] (FiddleIndex)))
     'fiddles FiddleIndex}
    (TutorialFiddles)
    (BlogFiddles)
    (ListedDemos)
    (SecretDemos)
    (Utilities)))

(e/defn ProdMain [ring-req]
  ; keep /tutorial/ in the URL
  (FiddleMain ring-req (Fiddles)
    :default '(tutorial)))
