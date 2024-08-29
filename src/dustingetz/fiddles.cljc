(ns dustingetz.fiddles
  (:require [hyperfiddle.electric-de :as e]
            [hyperfiddle :as hf]
            dustingetz.scratch
            [dustingetz.counter :refer [CounterDemo]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.scroll-abc :refer [Scroll-abc]]
            [dustingetz.scroll-dom :refer [ScrollDemo]]
            [dustingetz.dir-tree :refer [DirTreeDemo]]
            [dustingetz.fizzbuzz :refer [FizzBuzzDemo]]
            [dustingetz.fizzbuzz2 :refer [FizzBuzz2Demo]]
            [dustingetz.file-watcher :refer [FileWatcherDemo]]
            [dustingetz.electric-tree :refer [TreeDemo]]

            #_[dustingetz.scratch.demo-explorer-hfql :refer [DirectoryExplorer-HFQL]]
            #_[dustingetz.hfql-intro :refer [With-HFQL-Bindings
                                             Teeshirt-orders-1
                                             Teeshirt-orders-2
                                             Teeshirt-orders-3
                                             Teeshirt-orders-4
                                             Teeshirt-orders-5]]
            #_[dustingetz.y-fib :refer [Y-fib]]
            #_[dustingetz.y-dir :refer [Y-dir]]
            #_[dustingetz.essay :refer [Essay]]

            #_electric-fiddle.main
            ;#?(:clj models.teeshirt-orders-datomic)
            ))

(e/defn Fiddles []
  {`dustingetz.scratch/Scratch dustingetz.scratch/Scratch
   `CounterDemo CounterDemo
   `Painter Painter
   `ScrollDemo ScrollDemo
   `Scroll-abc Scroll-abc
   `DirTreeDemo DirTreeDemo
   `FizzBuzzDemo FizzBuzzDemo
   `FizzBuzz2Demo FizzBuzz2Demo
   `FileWatcherDemo FileWatcherDemo
   `TreeDemo TreeDemo
   ;`Y-fib Y-fib
   ;`Y-dir Y-dir
   ;`Essay (With-HFQL-Bindings. Essay)
   ;`Teeshirt-orders-1 (With-HFQL-Bindings. Teeshirt-orders-1)
   ;`Teeshirt-orders-2 (With-HFQL-Bindings. Teeshirt-orders-2)
   ;`Teeshirt-orders-3 (With-HFQL-Bindings. Teeshirt-orders-3)
   ;`Teeshirt-orders-4 (With-HFQL-Bindings. Teeshirt-orders-4)
   ;`Teeshirt-orders-5 (With-HFQL-Bindings. Teeshirt-orders-5)
   ;`DirectoryExplorer-HFQL (With-HFQL-Bindings. DirectoryExplorer-HFQL)
   })
