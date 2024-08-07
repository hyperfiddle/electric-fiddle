(ns london-talk-2024.fiddles
  (:require
   [hyperfiddle.electric-de :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]
   [electric-tutorial.demo-two-clocks :refer [TwoClocks]]
   [london-talk-2024.dir-tree :refer [Dir-tree-main]]
   [london-talk-2024.counter :refer [CounterDemo]]
   [london-talk-2024.webview :refer [Webview]]
   [london-talk-2024.diff-explainer :refer [DiffExplainer]]
   [london-talk-2024.differential-tricks :refer [DifferentialTricks]]
   [london-talk-2024.fizzbuzz :refer [FizzBuzzDemo]]))

(e/defn Hello []
  (e/client
    (dom/h1 (dom/text "Hello world"))))

(e/defn Fiddles [] {`Hello Hello
                    `TwoClocks TwoClocks
                    `Dir-tree-main Dir-tree-main
                    `CounterDemo CounterDemo
                    `Webview Webview
                    `DiffExplainer DiffExplainer
                    `DifferentialTricks DifferentialTricks
                    `FizzBuzzDemo FizzBuzzDemo})
