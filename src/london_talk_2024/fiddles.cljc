(ns london-talk-2024.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [london-talk-2024.two-clocks :refer [TwoClocks]]
   [london-talk-2024.dir-tree :refer [DirTreeDemo]]
   [london-talk-2024.counter :refer [CounterDemo]]
   [london-talk-2024.webview-generic :refer [WebviewGeneric]]
   [london-talk-2024.webview-concrete :refer [WebviewConcrete]]
   [london-talk-2024.differential-tricks :refer [DifferentialTricks]]
   [london-talk-2024.fizzbuzz :refer [FizzBuzzDemo]]))

(e/defn Fiddles []
  {`TwoClocks TwoClocks
   `DirTreeDemo DirTreeDemo
   `FizzBuzzDemo FizzBuzzDemo
   `WebviewConcrete WebviewConcrete
   `WebviewGeneric WebviewGeneric
   `DifferentialTricks DifferentialTricks
   `CounterDemo CounterDemo})
