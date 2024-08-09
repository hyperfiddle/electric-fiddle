(ns london-talk-2024.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [london-talk-2024.two-clocks :refer [TwoClocks]]
   [london-talk-2024.dir-tree :refer [DirTreeDemo]]
   [london-talk-2024.counter :refer [CounterDemo]]
   [london-talk-2024.webview :refer [Webview]]
   [london-talk-2024.webview-typeahead :refer [WebviewTypeahead]]
   [london-talk-2024.differential-tricks :refer [DifferentialTricks]]
   [london-talk-2024.fizzbuzz :refer [FizzBuzzDemo]]))

(e/defn Fiddles []
  {`TwoClocks TwoClocks
   `DirTreeDemo DirTreeDemo
   `FizzBuzzDemo FizzBuzzDemo
   `Webview Webview
   `DifferentialTricks DifferentialTricks
   `CounterDemo CounterDemo
   `WebviewTypeahead WebviewTypeahead})
