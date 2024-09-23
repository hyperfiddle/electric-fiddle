(ns dustingetz.fiddles
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle :as hf]
            [dustingetz.scratch :refer [Scratch]]
            [dustingetz.hello :refer [Hello]]
            [electric-tutorial.counter :refer [Counter]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.scroll-abc :refer [Scroll-abc]]
            [dustingetz.scroll-dom :refer [ScrollDemo]]
            [electric-tutorial.fizzbuzz :refer [FizzBuzzDemo]]
            [dustingetz.fizzbuzz2 :refer [FizzBuzz2Demo]]
            [dustingetz.file-watcher :refer [FileWatcherDemo]]
            [dustingetz.electric-tree :refer [TreeDemo]]
            [dustingetz.million-checkboxes :refer [MillionCheckboxes]]
            [dustingetz.million-checkboxes2 :refer [MillionCheckboxes2]]
            [dustingetz.chat2 :refer [Chat2]]
            [dustingetz.logic :refer [Logic]]


            #_[dustingetz.scratch.demo-explorer-hfql :refer [DirectoryExplorer-HFQL]]
            #_[dustingetz.hfql-intro :refer [With-HFQL-Bindings
                                             Teeshirt-orders-1
                                             Teeshirt-orders-2
                                             Teeshirt-orders-3
                                             Teeshirt-orders-4
                                             Teeshirt-orders-5]]
            #_[dustingetz.y-fib :refer [Y-fib]]
            #_[dustingetz.y-dir :refer [Y-dir]]
            [dustingetz.essay :refer [Essay]]

            #_electric-fiddle.main
            ;#?(:clj models.teeshirt-orders-datomic)
            ))

(e/defn Fiddles []
  {`Scratch Scratch
   `Hello Hello
   `Counter Counter
   `Painter Painter
   `Chat2 Chat2
   `ScrollDemo ScrollDemo
   `Scroll-abc Scroll-abc
   `FizzBuzzDemo FizzBuzzDemo
   `FizzBuzz2Demo FizzBuzz2Demo
   `FileWatcherDemo FileWatcherDemo
   `TreeDemo TreeDemo
   `MillionCheckboxes MillionCheckboxes
   `MillionCheckboxes2 MillionCheckboxes2
   `Logic Logic
   ;`Y-fib Y-fib
   ;`Y-dir Y-dir
   `Essay Essay #_(With-HFQL-Bindings. Essay)
   ;`Teeshirt-orders-1 (With-HFQL-Bindings. Teeshirt-orders-1)
   ;`Teeshirt-orders-2 (With-HFQL-Bindings. Teeshirt-orders-2)
   ;`Teeshirt-orders-3 (With-HFQL-Bindings. Teeshirt-orders-3)
   ;`Teeshirt-orders-4 (With-HFQL-Bindings. Teeshirt-orders-4)
   ;`Teeshirt-orders-5 (With-HFQL-Bindings. Teeshirt-orders-5)
   ;`DirectoryExplorer-HFQL (With-HFQL-Bindings. DirectoryExplorer-HFQL)
   })
