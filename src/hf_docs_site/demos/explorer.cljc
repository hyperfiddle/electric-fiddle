(ns hf-docs-site.demos.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister]]
            [contrib.str :refer [includes-str?]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router3 :as r]))

(def unicode-folder "\uD83D\uDCC2") ; 📂

(e/declare base-path)

(e/defn Render-cell [m a]
  (let [v (a m)
        txt (if (and (= ::fs/name a) (= ::fs/dir (::fs/kind m)))
              (let [path (e/server (fs/relativize-path base-path (::fs/absolute-path m)))]
                (r/link #_['.. 0 path] ['.. [path ""]] ; discard search
                  (dom/text v)) nil)
              (case a
                ::fs/name v
                ::fs/modified (e/client (some-> v .toLocaleDateString))
                ::fs/kind (case (::fs/kind m) ::fs/dir unicode-folder #_else (e/client (some-> v name)))
                #_else (e/client (str v))))]
    (some-> txt dom/text)))

(e/defn Row [i x tab]
  (dom/tr (dom/props {:style {:--order (inc i)} #_#_:data-row-stripe (mod i 2)}) ; striping damages perf slightly
    (dom/td (Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
    (dom/td (Render-cell x ::fs/modified))
    (dom/td (Render-cell x ::fs/size))
    (dom/td (Render-cell x ::fs/kind))))

(e/defn TableScroll [xs! #_& {:keys [row-height record-count overquery-factor]}]
  (dom/div (dom/props {:class "Viewport"})
    (let [[offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
      (e/client
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [[i [tab x]] (e/server
                                ((fn [i] [i (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children)])
                                 (IndexRing limit offset)))]
            (Row i x tab))))
      (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}})))))

(def css "
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; position:fixed; top:8em; bottom:0; left:0; right:0; }
.DirectoryExplorer table { /* position: relative; */ /* position: sticky; top:0; */ }
.DirectoryExplorer table { display: grid; grid-template-columns: auto 8em 8em 10em; }
.DirectoryExplorer table tr { display: contents; visibility: var(--visibility); }
.DirectoryExplorer table td { height: 24px; }
.DirectoryExplorer table tr td { grid-row: var(--order); }
/* .DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; } */
.DirectoryExplorer table tr:hover td { background-color: #ddd; }")

(e/defn Dir [x TableScroll]
  (e/server
    (let [m (datafy x)
          xs! (seq ((treelister ::fs/children #(includes-str? (::fs/name %) %2)
                      (nav m ::fs/children (::fs/children m))) ""))]
      (dom/h1 (dom/text (::fs/absolute-path m) " (" (count xs!) " items)"))
      (TableScroll xs! {:row-height 24 :overquery-factor 1 :record-count (count xs!)}))))

(e/defn AppRoot [TableScroll]
  (dom/style (dom/text css))
  (dom/div (dom/props {:class "DirectoryExplorer"})
    (let [[fs-rel-path search] r/route]
      (if-not fs-rel-path
        (r/ReplaceState! ['. ["src" (or search "")]])
        (r/pop (binding [base-path (e/server (fs/absolute-path "./"))]
                 (Dir (e/server (clojure.java.io/file base-path fs-rel-path)) TableScroll)))))))

(e/defn DirectoryExplorer []
  (AppRoot TableScroll))

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
