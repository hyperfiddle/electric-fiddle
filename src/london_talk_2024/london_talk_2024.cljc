(ns london-talk-2024.london-talk-2024
  (:require
   [hyperfiddle.electric3 :as e]
   [london-talk-2024.webview-concrete :refer [WebviewConcrete]]
   [london-talk-2024.webview-generic :refer [WebviewGeneric]]
   [london-talk-2024.webview-dynamic :refer [WebviewDynamic]]
   [london-talk-2024.webview-scroll :refer [WebviewScroll]]
   [london-talk-2024.differential-tricks :refer [DifferentialTricks]]))

(e/defn Fiddles []
  {`WebviewConcrete WebviewConcrete
   `WebviewGeneric WebviewGeneric
   `WebviewDynamic WebviewDynamic
   `WebviewScroll WebviewScroll
   `DifferentialTricks DifferentialTricks})
