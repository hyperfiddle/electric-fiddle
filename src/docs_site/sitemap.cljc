(ns docs-site.sitemap
  (:require [hyperfiddle.electric3 :as e]
            [electric-fiddle.fiddle-index :refer [FiddleMain FiddleIndex]]
            [electric-essay.tutorial-app :refer [Tutorial Examples]]
            [electric-essay.essay-app :refer [Essay]]
            [dustingetz.datomic-browser :refer [DatomicBrowser ConnectDatomic #?(:clj datomic-browser-sitemap)]]
            [dustingetz.datomic-browser2 :as datomic-browser2]
            [docs-site.blog.index :refer [BlogIndex]]
            [dustingetz.unifhir1 :refer [Unifhir1]]
            [docs-site.blog.waveform0 :refer [Waveform0]]
            [docs-site.tutorial-sitemap :refer [TutorialFiddles tutorial-sitemap]]
            [electric-tutorial.explorer :refer [DirectoryExplorer]]
            [dustingetz.object-browser-demo3 :as ob]
            [dustingetz.talks.two-clocks]
            [dustingetz.talks.dir-tree]
            [dustingetz.talks.webview-concrete]
            [dustingetz.talks.lifecycle]
            [electric-examples.inputs-basic]
            [electric-examples.reactive-collections]
            [electric-examples.discrete-events]
            [electric-examples.transaction]
            [electric-examples.http-request]
            [electric-examples.simple-form-controls]
            [electric-examples.simple-form]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.router5 :as router5]
            [hyperfiddle.history4 :as history]
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
   `Waveform0 Waveform0})

(e/defn Utilities []
  {`ob/ObjectBrowserDemo3 ob/ObjectBrowserDemo3})

(e/defn SecretDemos []
  (merge
    (staffly.staffly/Fiddles)
    ))

(e/defn ListedDemos []
  {'datomic-browser.mbrainz-browser/DatomicBrowser (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser)]))   ; redirect
   `datomic-browser.datomic-browser4/DatomicBrowser4 (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser)])) ; redirect
   #_#_`datomic-browser.datomic-browser/DatomicBrowser (e/fn [& _] (r/ReplaceState! ['/ `(DatomicBrowser)]))   ; redirect for readme @ https://github.com/hyperfiddle/electric-datomic-browser
   `DatomicBrowser (e/fn [] (e/server (DatomicBrowser (e/server datomic-browser-sitemap)
                                        '[(dustingetz.datomic-browser/attributes)]
                                        (ConnectDatomic (get-datomic-uri))))) ; default prod demo dataset is mbrainz
   `datomic-browser2/DatomicBrowser (e/fn []
                                      (e/server (datomic-browser2/DatomicBrowser (e/server datomic-browser2/datomic-browser-sitemap)
                                                  '[(dustingetz.datomic-browser2/attributes)]
                                                  (datomic-browser2/ConnectDatomic (get-datomic-uri))))) ; default prod demo dataset is mbrainz


   `Unifhir1 Unifhir1
   `DirectoryExplorer DirectoryExplorer})

(e/defn TalkDemos-LC2025 [] ; works without :dustingetz alias
  {`dustingetz.talks.two-clocks/TwoClocks dustingetz.talks.two-clocks/TwoClocks
   `dustingetz.talks.dir-tree/DirTree dustingetz.talks.dir-tree/DirTree
   `dustingetz.talks.webview-concrete/WebviewConcrete dustingetz.talks.webview-concrete/WebviewConcrete
   `dustingetz.talks.lifecycle/Lifecycle dustingetz.talks.lifecycle/Lifecycle})

(e/defn DocsExamples []
  {
   `electric-examples.inputs-basic/UncontrolledInputDemo electric-examples.inputs-basic/UncontrolledInputDemo
   `electric-examples.reactive-collections/ReactiveCollections electric-examples.reactive-collections/ReactiveCollections
   `electric-examples.discrete-events/ButtonClick electric-examples.discrete-events/ButtonClick
   `electric-examples.transaction/Transaction electric-examples.transaction/Transaction
   `electric-examples.http-request/HttpRequest electric-examples.http-request/HttpRequest
   `electric-examples.simple-form-controls/SimpleFormControls electric-examples.simple-form-controls/SimpleFormControls
   `electric-examples.simple-form/SimpleForm electric-examples.simple-form/SimpleForm
   })

(e/defn Fiddles []
  (merge
    {'tutorial (e/Partial Tutorial tutorial-sitemap "src/electric_tutorial/")
     'examples (e/Partial Examples tutorial-sitemap "src/electric_examples/")
     'blog (e/Partial Essay blog-sitemap "src/docs_site/blog/")
     'demos (e/fn [] (binding [electric-fiddle.fiddle-index/pages (ListedDemos)] (FiddleIndex)))
     'fiddles FiddleIndex}
    (TutorialFiddles)
    (BlogFiddles)
    (ListedDemos)
    (SecretDemos)
    (TalkDemos-LC2025)
    (DocsExamples)
    (Utilities)))

(e/defn ProdMain [ring-req]
  ; keep /tutorial/ in the URL
  (FiddleMain ring-req (Fiddles)
    :default '(tutorial)))
