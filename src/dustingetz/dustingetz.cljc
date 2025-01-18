(ns dustingetz.dustingetz
  (:require [hyperfiddle.electric3 :as e]
            [dustingetz.easy-table :refer [DemoEasyTable]]
            [dustingetz.edn-viewer0 :refer [EdnViewer0]]
            [dustingetz.file-watcher :refer [FileWatcherDemo]]
            [dustingetz.logic :refer [Logic]]
            [dustingetz.london-talk-2024.webview-concrete :refer [WebviewConcrete]]
            [dustingetz.london-talk-2024.webview-generic :refer [WebviewGeneric]]
            [dustingetz.london-talk-2024.webview-dynamic :refer [WebviewDynamic]]
            [dustingetz.london-talk-2024.webview-scroll :refer [WebviewScroll]]
            [dustingetz.london-talk-2024.webview-scroll-dynamic :refer [WebviewScrollDynamic]]
            [dustingetz.london-talk-2024.differential-tricks :refer [DifferentialTricks]]
            [dustingetz.metaobject :refer [DemoMetaobject]]
            [dustingetz.million-checkboxes :refer [MillionCheckboxes]]
            [dustingetz.million-checkboxes2 :refer [MillionCheckboxes2]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.threaddump :refer [ThreadDump]]
            [dustingetz.y-fac :refer [Y-Fac]]
            [dustingetz.y-dir :refer [Y-dir]]))

(e/defn Fiddles []
  (merge
    {`DemoEasyTable DemoEasyTable
     `ThreadDump ThreadDump
     `EdnViewer0 EdnViewer0}
    {`DemoMetaobject DemoMetaobject
     `Painter Painter
     `FileWatcherDemo FileWatcherDemo
     `Y-Fac Y-Fac
     `Y-dir Y-dir
     `MillionCheckboxes MillionCheckboxes
     `MillionCheckboxes2 MillionCheckboxes2
     `Logic Logic}
    {`WebviewConcrete WebviewConcrete
     `WebviewGeneric WebviewGeneric
     `WebviewDynamic WebviewDynamic
     `WebviewScroll WebviewScroll
     `WebviewScrollDynamic WebviewScrollDynamic
     `DifferentialTricks DifferentialTricks}))
