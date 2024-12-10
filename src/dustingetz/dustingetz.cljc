(ns dustingetz.dustingetz
  (:require [hyperfiddle.electric3 :as e]
            [dustingetz.electric-tree :refer [TreeDemo]]
            [dustingetz.essay :refer [Essay]]
            [dustingetz.explorer :refer [DirectoryExplorer]]
            [dustingetz.file-watcher :refer [FileWatcherDemo]]
            [dustingetz.logic :refer [Logic]]
            [dustingetz.london-talk-2024.webview-concrete :refer [WebviewConcrete]]
            [dustingetz.london-talk-2024.webview-generic :refer [WebviewGeneric]]
            [dustingetz.london-talk-2024.webview-dynamic :refer [WebviewDynamic]]
            [dustingetz.london-talk-2024.webview-scroll :refer [WebviewScroll]]
            [dustingetz.london-talk-2024.differential-tricks :refer [DifferentialTricks]]
            [dustingetz.metaobject :refer [DemoMetaobject]]
            [dustingetz.million-checkboxes :refer [MillionCheckboxes]]
            [dustingetz.million-checkboxes2 :refer [MillionCheckboxes2]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.scratch :refer [Scratch]]
            [dustingetz.y-fac :refer [Y-Fac]]
            [dustingetz.y-dir :refer [Y-dir]]
            [scratch.dustin.y2024.scroll-abc :refer [Scroll-abc]]
            [scratch.dustin.y2024.scroll-dom :refer [ScrollDemo]]
            #_[dustingetz.scratch.demo-explorer-hfql :refer [DirectoryExplorer-HFQL]]
            #_[dustingetz.hfql-intro :refer [With-HFQL-Bindings
                                             Teeshirt-orders-1
                                             Teeshirt-orders-2
                                             Teeshirt-orders-3
                                             Teeshirt-orders-4
                                             Teeshirt-orders-5]]
            ))

(e/defn Fiddles []
  {`Scratch Scratch
   `DirectoryExplorer DirectoryExplorer
   `DemoMetaobject DemoMetaobject
   `Painter Painter
   `FileWatcherDemo FileWatcherDemo
   `Y-Fac Y-Fac
   `Y-dir Y-dir
   `TreeDemo TreeDemo
   `MillionCheckboxes MillionCheckboxes
   `MillionCheckboxes2 MillionCheckboxes2
   `Logic Logic

   `ScrollDemo ScrollDemo
   `Scroll-abc Scroll-abc

   `WebviewConcrete WebviewConcrete
   `WebviewGeneric WebviewGeneric
   `WebviewDynamic WebviewDynamic
   `WebviewScroll WebviewScroll
   `DifferentialTricks DifferentialTricks
   `Essay Essay #_(With-HFQL-Bindings. Essay)
   ;`Teeshirt-orders-1 (With-HFQL-Bindings. Teeshirt-orders-1)
   ;`Teeshirt-orders-2 (With-HFQL-Bindings. Teeshirt-orders-2)
   ;`Teeshirt-orders-3 (With-HFQL-Bindings. Teeshirt-orders-3)
   ;`Teeshirt-orders-4 (With-HFQL-Bindings. Teeshirt-orders-4)
   ;`Teeshirt-orders-5 (With-HFQL-Bindings. Teeshirt-orders-5)
   ;`DirectoryExplorer-HFQL (With-HFQL-Bindings. DirectoryExplorer-HFQL)
   })
