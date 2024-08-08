(ns dustingetz.fiddles
  (:require [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle :as hf]
            dustingetz.scratch
            [dustingetz.peter-counter :refer [Counter]]
            [dustingetz.painter :refer [Painter]]
            [dustingetz.scroll-abc :refer [Scroll1]]
            [dustingetz.scroll-dom :refer [ScrollDemo]]

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
   `Counter Counter
   `Painter Painter
   `ScrollDemo ScrollDemo
   `Scroll1 Scroll1
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
