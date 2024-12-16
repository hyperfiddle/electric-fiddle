(ns dustingetz.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :as scroll :refer [Scroll-indexed-headless]]
            [hyperfiddle.router3 :as r]))

(def unicode-folder "\uD83D\uDCC2") ; 📂

(e/defn Render-cell [m a]
  (let [v (a m)]
    (case a
      ::fs/name (case (::fs/kind m)
                  ::fs/dir (let [absolute-path (::fs/absolute-path m)]
                             (r/link #_['.. 0 absolute-path] ['.. [absolute-path ""]] ; discard search
                               (dom/text v)))
                  (::fs/other ::fs/symlink ::fs/unknown-kind) (dom/text v)
                  (dom/text v))
      ::fs/modified (dom/text (e/client (some-> v .toLocaleDateString)))
      ::fs/kind (case (::fs/kind m)
                  ::fs/dir (dom/text unicode-folder)
                  (dom/text (e/client (some-> v name))))
      (dom/text (e/client (str v))))))

(e/defn TableScroll [xs! #_& {:as props}]
  (dom/div (dom/props {:class "Viewport"})
    (let [{::scroll/keys [row-height Offset limit record-count Spool]}
          (Scroll-indexed-headless dom/node xs! props)]
      (e/client
        (dom/table (dom/props {:style {:top (str (* (Offset) row-height) "px")}})
          (e/for [[i [tab x]] (e/server (Spool))]
            (dom/tr
              (dom/td (Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
              (dom/td (Render-cell x ::fs/modified))
              (dom/td (Render-cell x ::fs/size))
              (dom/td (Render-cell x ::fs/kind))))))
      (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}})))))

(def css "
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; position:fixed; top:8em; bottom:0; left:0; right:0; }
.DirectoryExplorer table { position: relative; display: grid; grid-template-columns: auto 8em 8em 10em; }
.DirectoryExplorer table tr { display: contents; }
.DirectoryExplorer table td { height: 24px; }
.DirectoryExplorer table tr:nth-child(even) td { background-color: #f2f2f2; }
.DirectoryExplorer table tr:hover td { background-color: #ddd; }")

(e/defn Dir [x]
  (e/server
    (let [m (datafy x)
          xs! (seq ((treelister ::fs/children #(includes-str? (::fs/name %) %2)
                      (nav m ::fs/children (::fs/children m))) ""))]
      (dom/h1 (dom/text (::fs/absolute-path m) " (" (count xs!) " items)"))
      (TableScroll xs! {:row-height 24 :overquery-factor 3}))))

(e/defn DirectoryExplorer []
  (dom/style (dom/text css))
  (dom/div (dom/props {:class "DirectoryExplorer"})
    (let [[fs-path search] r/route]
      (if-not fs-path
        (r/ReplaceState! ['. [(e/server (fs/absolute-path "./src/")) (or search "")]])
        (r/pop (Dir (e/server (clojure.java.io/file fs-path))))))))

(comment
  (def m (datafy (clojure.java.io/file (fs/absolute-path "./"))))
  (def xs (nav m ::fs/children (::fs/children m)))
  (def xs ((treelister ::fs/children #(includes-str? (::fs/name %) %2) xs) ""))
  (count (seq xs))
  (def qs (take 10 xs)))





; Improvements
; Native search
; lazy folding/unfolding directories (no need for pagination)
; forms (currently table hardcoded with recursive pull)
