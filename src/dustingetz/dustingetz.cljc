(ns dustingetz.dustingetz
  (:require [hyperfiddle.electric3 :as e]
            [dustingetz.file-watcher :refer [FileWatcherDemo]]
            [dustingetz.object-browser-demo3 :refer [ObjectBrowserDemo3]]
            [dustingetz.logic :refer [Logic]]
            [dustingetz.metaobject :refer [DemoMetaobject]]
            [dustingetz.million-checkboxes :refer [MillionCheckboxes]]
            [dustingetz.million-checkboxes2 :refer [MillionCheckboxes2]]
            [dustingetz.navigator-demo1 :refer [NavigatorDemo1]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.y-fac :refer [Y-Fac]]
            [dustingetz.y-dir :refer [Y-dir]]

            [dustingetz.talks.two-clocks :refer [TwoClocks]]
            [dustingetz.talks.dir-tree :refer [DirTree]]
            [dustingetz.talks.webview-concrete :refer [WebviewConcrete]]
            [dustingetz.talks.lifecycle :refer [Lifecycle]]))

(e/defn Fiddles []
  (merge
    ;; {`NavigatorDemo1 NavigatorDemo1}
    {`TwoClocks TwoClocks
     `DirTree DirTree
     `WebviewConcrete WebviewConcrete
     `Lifecycle Lifecycle}
    {`DemoMetaobject DemoMetaobject
     `Painter Painter
     `FileWatcherDemo FileWatcherDemo
     `Y-Fac Y-Fac
     `Y-dir Y-dir
     `MillionCheckboxes MillionCheckboxes
     `MillionCheckboxes2 MillionCheckboxes2
     `Logic Logic
     `ObjectBrowserDemo3 ObjectBrowserDemo3}
    ))
