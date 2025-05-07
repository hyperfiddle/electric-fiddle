(ns docs-site.sitemap
  (:require [hyperfiddle.electric3 :as e]
            [electric-fiddle.fiddle-index :refer [FiddleMain FiddleIndex]]
            [electric-essay.tutorial-app :refer [Tutorial]]
            [electric-essay.essay-app :refer [Essay]]
            [hyperfiddle.datomic-browser-demo :refer [DatomicBrowser ConnectDatomic]]
            [docs-site.blog.index :refer [BlogIndex]]
            [dustingetz.unifhir1 :refer [Unifhir1]]
            [docs-site.blog.threaddump1 :refer [ThreadDump1]]
            [docs-site.blog.threaddump2 :refer [ThreadDump2]]
            [docs-site.blog.threaddump3 :refer [ThreadDump3]]
            [docs-site.blog.waveform0 :refer [Waveform0]]
            [docs-site.tutorial-sitemap :refer [TutorialFiddles tutorial-sitemap]]
            [electric-tutorial.explorer :refer [DirectoryExplorer]]
            [dustingetz.object-browser-demo3 :as ob]
            [hyperfiddle.router4 :as r]
            staffly.staffly
            #?(:clj dustingetz.mbrainz)
            #?(:clj datomic-browser.users-with-email-db)))

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
  {`ob/ObjectBrowserDemo3 ob/ObjectBrowserDemo3})

(e/defn SecretDemos []
  (merge
    (staffly.staffly/Fiddles)
    ))

#?(:clj (defn get-datomic-uri []
          (or (System/getProperty "hyperfiddle.datomic.uri") dustingetz.mbrainz/mbrainz-uri)))

(e/defn ListedDemos []
  {'datomic-browser.mbrainz-browser/DatomicBrowser (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser)]))   ; redirect
   `datomic-browser.datomic-browser4/DatomicBrowser4 (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser)])) ; redirect
   `DatomicBrowser (e/fn [] (DatomicBrowser (e/server (ConnectDatomic (get-datomic-uri))))) ; default prod dataset is mbrainz
   ;; `db4/DatomicBrowser4 (e/fn [] (db4/DatomicBrowser4 datomic-browser.users-with-email-db/conn))
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
