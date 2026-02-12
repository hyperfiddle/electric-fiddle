(ns docs-site.sitemap
  (:require
    #?(:clj [dustingetz.mbrainz :refer [datomic-uri mbrainz-uri!]]) ;; [dustingetz.datomic-browser :refer [DatomicBrowser ConnectDatomic #?(:clj datomic-browser-sitemap)]]
    [dustingetz.datomic-browser2 :as datomic-browser2]
    [docs-site.blog.index :refer [BlogIndex]]
    [docs-site.blog.waveform0 :refer [Waveform0]]
    [docs-site.tutorial-sitemap :refer [tutorial-sitemap TutorialFiddles]]
    dustingetz.navigator-demo1
    [dustingetz.object-browser-demo3 :as ob]
    [dustingetz.talks.dir-tree]
    [dustingetz.talks.lifecycle]
    [dustingetz.talks.two-clocks]
    [dustingetz.talks.webview-concrete]
    [dustingetz.unifhir1 :refer [Unifhir1]]
    [electric-examples.discrete-events]
    [electric-examples.http-request]
    [electric-examples.inputs-basic]
    [electric-examples.reactive-collections]
    [electric-examples.simple-form]
    [electric-examples.simple-form-controls]
    [electric-examples.transaction]
    [electric-fiddle.fiddle :refer [Fiddle]] ;; [dustingetz.datomic-browser :refer [DatomicBrowser ConnectDatomic #?(:clj datomic-browser-sitemap)]]
    [electric-fiddle.fiddle-index :refer [FiddleIndex FiddleMain]]
    [electric-tutorial.explorer :refer [DirectoryExplorer]]
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.router5 :as r]
    staffly.staffly))

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
    (staffly.staffly/Fiddles)))

#?(:clj (defn get-datomic-uri [] (or (System/getProperty "hyperfiddle.datomic.uri") (mbrainz-uri!))))

(e/defn ListedDemos []
  {
   `datomic-browser2/DatomicBrowser (e/fn []
                                      (e/server (datomic-browser2/BrowseDatomicByURI (e/server datomic-browser2/sitemap)
                                                  ['databases]
                                                  (str datomic-uri "*"))))

   `dustingetz.navigator-demo1/NavigatorDemo1
   (e/server (e/fn [] (dustingetz.navigator-demo1/NavigatorDemo1 (e/server dustingetz.navigator-demo1/sitemap) ['pages])))

   `Unifhir1 Unifhir1
   `DirectoryExplorer DirectoryExplorer})

(e/defn TalkDemos-LC2025 [] ; works without :dustingetz alias
  {`dustingetz.talks.two-clocks/TwoClocks dustingetz.talks.two-clocks/TwoClocks
   `dustingetz.talks.dir-tree/DirTree dustingetz.talks.dir-tree/DirTree
   `dustingetz.talks.webview-concrete/WebviewConcrete dustingetz.talks.webview-concrete/WebviewConcrete
   `dustingetz.talks.lifecycle/Lifecycle dustingetz.talks.lifecycle/Lifecycle})

(e/defn DocsExamples []
  {`electric-examples.inputs-basic/UncontrolledInputDemo electric-examples.inputs-basic/UncontrolledInputDemo
   `electric-examples.reactive-collections/ReactiveCollections electric-examples.reactive-collections/ReactiveCollections
   `electric-examples.discrete-events/ButtonClick electric-examples.discrete-events/ButtonClick
   `electric-examples.transaction/Transaction electric-examples.transaction/Transaction
   `electric-examples.http-request/HttpRequest electric-examples.http-request/HttpRequest
   `electric-examples.simple-form-controls/SimpleFormControls electric-examples.simple-form-controls/SimpleFormControls
   `electric-examples.simple-form/SimpleForm electric-examples.simple-form/SimpleForm})

(e/defn Fiddles []
  (merge
    {#_#_'tutorial (e/Partial Tutorial tutorial-sitemap "src/electric_tutorial/")
     #_#_'examples (e/Partial Examples tutorial-sitemap "src/electric_examples/")
     'fiddle (e/Partial Fiddle tutorial-sitemap "src/electric_examples/")
     #_#_'blog (e/Partial Essay blog-sitemap "src/docs_site/blog/")
     #_#_'demos (e/fn [] (binding [electric-fiddle.fiddle-index/pages (ListedDemos)] (FiddleIndex)))
     'fiddles FiddleIndex}
    (TutorialFiddles)                    ; it's a google doc now
    (BlogFiddles)
    (ListedDemos)
    (SecretDemos)
    (TalkDemos-LC2025)
    (DocsExamples)
    (Utilities)))

(e/defn ProdMain [ring-req]
  ; keep /tutorial/ in the URL
  (FiddleMain ring-req (Fiddles)
    #_#_:default '(tutorial))) ; todo electric kwargs don't pass all the way down
