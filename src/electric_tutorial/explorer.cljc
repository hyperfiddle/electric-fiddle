(ns electric-tutorial.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister clamp-left]]
            [contrib.str :refer [includes-str?]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router3 :as router]))

(def unicode-folder "\uD83D\uDCC2") ; 📂
(e/declare base-path)

(e/defn Render-cell [?m a]
  (e/server
    (let [?v (a ?m)
          dir? (= ::fs/dir (::fs/kind ?m))]
      (case a
        ::fs/name (if dir? ; fixme blinks on switch due to unexplained latency - electric issue?
                    (if-some [path (some->> ?m ::fs/absolute-path (fs/relativize-path base-path))] ; guard glitch
                      (router/link ['.. [path]] (dom/text ?v)))
                    (dom/text ?v))
        ::fs/modified (dom/text (e/client (some-> ?v .toLocaleDateString)))
        ::fs/kind (dom/text (if dir? unicode-folder (some-> ?v name)))
        (dom/text (str ?v))))))

(e/defn Row [i ?x]
  (e/client
    (let [?tab (e/server (some-> ?x (nth 0)))
          ?x (e/server (some-> ?x (nth 1)))]
      (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
        (dom/td (Render-cell ?x ::fs/name) (dom/props {:style {:padding-left (some-> ?tab (* 15) (str "px"))}}))
        (dom/td (Render-cell ?x ::fs/modified))
        (dom/td (Render-cell ?x ::fs/size))
        (dom/td (Render-cell ?x ::fs/kind))))))

(e/defn TableScroll [xs! #_& {:keys [row-height record-count overquery-factor]}]
  (e/server
    (dom/props {:class "Viewport"})
    (let [record-count (e/server (or record-count (count xs!)))
          [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
      (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
        (e/for [i (IndexRing limit offset)] ; render all rows even with fewer elements
          (Row i (e/server (nth xs! i nil)))))
      (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                  (* row-height (- record-count limit)) 0) "px")}})))))

(e/defn Dir [m]
  (e/server
    (let [xs! (seq ((treelister ::fs/children #(includes-str? (::fs/name %) %2)
                      (nav m ::fs/children (::fs/children m))) ""))]

      (dom/fieldset (dom/legend (dom/text (::fs/absolute-path m) " (" (count xs!) " items)"))
        (TableScroll xs! {:row-height 24 :overquery-factor 1})))))

(declare css)
(e/defn DirectoryExplorer []
  (dom/style (dom/text css))
  (dom/div (dom/props {:class "DirectoryExplorer"})
    (let [[fs-rel-path] router/route]
      (if-not fs-rel-path
        (router/ReplaceState! ['. ["src"]])
        (router/pop
          (binding [base-path (e/server (fs/absolute-path "./"))]
            (Dir (e/server (datafy (clojure.java.io/file base-path fs-rel-path))))))))))

(comment
  (def m (datafy (clojure.java.io/file (fs/absolute-path "./"))))
  (def xs (nav m ::fs/children (::fs/children m)))
  (def xs ((treelister ::fs/children #(includes-str? (::fs/name %) %2) xs) ""))
  (count (seq xs))
  (def qs (take 10 xs)))

(def css "
/* Scroll machinery */
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; }
.DirectoryExplorer table { display: grid; }
.DirectoryExplorer table tr { display: contents; visibility: var(--visibility); }
.DirectoryExplorer table td { grid-row: var(--order); }

/* fullscreen, except in tutorial mode */
.Tutorial > .DirectoryExplorer fieldset.Viewport { height: 30em; } /* max-height doesn't work - fieldset quirk */
:not(.Tutorial) > .DirectoryExplorer .Viewport { position:fixed; top:0em; bottom:0; left:0; right:0; }

/* Cosmetic styles */
.DirectoryExplorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.DirectoryExplorer legend { margin-left: 1em; font-size: larger; }
.DirectoryExplorer table { grid-template-columns: auto 6em 5em 3em; }
.DirectoryExplorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.DirectoryExplorer table tr:hover td { background-color: #ddd; }
.DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }")