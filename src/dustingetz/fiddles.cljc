(ns dustingetz.fiddles
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle :as hf]
            ;; [dustingetz.essay :refer [Essay]]
            [dustingetz.explorer :refer [DirectoryExplorer]]
            ;; [dustingetz.file-watcher :refer [FileWatcherDemo]]
            ;; [dustingetz.scratch :refer [Scratch]]
            ;; [dustingetz.million-checkboxes :refer [MillionCheckboxes]]
            ;; [dustingetz.million-checkboxes2 :refer [MillionCheckboxes2]]
            ;; [dustingetz.logic :refer [Logic]]
            ;; [dustingetz.painter :refer [Painter]]
            ;; [scratch.dustin.y2024.scroll-abc :refer [Scroll-abc]]
            ;; [scratch.dustin.y2024.scroll-dom :refer [ScrollDemo]]
            ;; [scratch.dustin.y2024.electric-tree :refer [TreeDemo]]
            #_[dustingetz.scratch.demo-explorer-hfql :refer [DirectoryExplorer-HFQL]]
            #_[dustingetz.hfql-intro :refer [With-HFQL-Bindings
                                             Teeshirt-orders-1
                                             Teeshirt-orders-2
                                             Teeshirt-orders-3
                                             Teeshirt-orders-4
                                             Teeshirt-orders-5]]
            #_[dustingetz.y-fib :refer [Y-fib]]
            #_[dustingetz.y-dir :refer [Y-dir]]
            #_electric-fiddle.main
            ;#?(:clj models.teeshirt-orders-datomic)
            #_[dustingetz.metaobject :refer [DemoMetaobject]]
            ))

(e/defn Fiddles []
  {
  ;;  `Scratch Scratch
  ;;  `Painter Painter
  ;;  `ScrollDemo ScrollDemo
  ;;  `Scroll-abc Scroll-abc
  ;;  `FileWatcherDemo FileWatcherDemo
  ;;  `TreeDemo TreeDemo
  ;;  `MillionCheckboxes MillionCheckboxes
  ;;  `MillionCheckboxes2 MillionCheckboxes2
  ;;  `Logic Logic
   `DirectoryExplorer DirectoryExplorer
   ;`DemoMetaobject DemoMetaobject
   ;`Y-fib Y-fib
   ;`Y-dir Y-dir
   ;`Essay Essay #_(With-HFQL-Bindings. Essay)
   ;`Teeshirt-orders-1 (With-HFQL-Bindings. Teeshirt-orders-1)
   ;`Teeshirt-orders-2 (With-HFQL-Bindings. Teeshirt-orders-2)
   ;`Teeshirt-orders-3 (With-HFQL-Bindings. Teeshirt-orders-3)
   ;`Teeshirt-orders-4 (With-HFQL-Bindings. Teeshirt-orders-4)
   ;`Teeshirt-orders-5 (With-HFQL-Bindings. Teeshirt-orders-5)
   ;`DirectoryExplorer-HFQL (With-HFQL-Bindings. DirectoryExplorer-HFQL)
   })
