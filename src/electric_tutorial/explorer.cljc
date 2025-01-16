(ns electric-tutorial.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.assert :refer [check]]
            [contrib.data :refer [clamp-left]]
            [contrib.str :refer [includes-str?]]
            [contrib.treelister :refer [treelister]]
            [dustingetz.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms3 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.router4 :as router]
            [missionary.core :as m]))

(def unicode-folder "\uD83D\uDCC2") ; 📂
(e/declare base-path)

(e/defn Render-cell [?m a]
  (e/client
    (let [?v (e/server (a ?m))
          dir? (e/server (= ::fs/dir (::fs/kind ?m)))
          path (e/server (and dir? (some->> ?m ::fs/absolute-path (fs/relativize-path base-path))))]
      path ?v dir? ; prefetch initial load - saves a blink
      (case a
        ::fs/name (if (and dir? path)
                    (router/link ['.. [path]] (dom/text ?v))
                    (dom/text (str ?v)))
        ::fs/modified (dom/text (e/client (some-> ?v .toLocaleDateString)))
        ::fs/kind (dom/text (if dir? unicode-folder (some-> ?v name)))
        (dom/text (str ?v))))))

(e/defn Row [i ?x]
  (e/client
    (let [?tab (e/server (some-> ?x (nth 0))) ; destructure on server, todo electric can auto-site this
          ?x (e/server (some-> ?x (nth 1) datafy))]
      (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
        (dom/td (Render-cell ?x ::fs/name) (dom/props {:style {:padding-left (some-> ?tab (* 15) (str "px"))}}))
        (dom/td (Render-cell ?x ::fs/modified))
        (dom/td (Render-cell ?x ::fs/size))
        (dom/td (Render-cell ?x ::fs/kind))))))

(e/defn TableScroll [record-count xs!]
  (e/server
    (dom/props {:class "Viewport"})
    (let [row-height 24
          [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
      (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
        (e/for [i (IndexRing limit offset)] ; render all rows even with fewer elements
          (Row i (e/server (nth xs! i nil)))))
      (dom/div (dom/props {:style {:height (str (clamp-left ; row count can exceed record count
                                                  (* row-height (- record-count limit)) 0) "px")}})))))

(defn hidden-or-node-modules [m] (or (::fs/hidden m) (= "node_modules" (::fs/name m))))

(e/defn Dir [x]
  (e/server
    (let [!search (atom "") search (e/watch !search)
          m (datafy x)
          xs! #_(e/Task (m/via m/blk)) #_(ex/Offload (fn []))
          (vec ((treelister
                  ; search over 10k+ records is too slow w/o a search index, so remove node_modules and .git
                  (fn children [m] (if (not (hidden-or-node-modules m)) (if-some [z (::fs/children m)] (z))))
                  (fn keep? [m search] (and (not (hidden-or-node-modules m)) (includes-str? (::fs/name m) search)))
                  (nav m ::fs/children ((::fs/children m)))) search))
          n (count xs!)]
      (dom/fieldset (dom/legend (dom/text (::fs/absolute-path m) " ")
                      (do (reset! !search (e/client (Input* ""))) nil) (dom/text " (" n " items)"))
        (dom/div ; viewport is underneath the dom/legend and must have pixel perfect height
          (TableScroll n xs!))))))

(declare css)
(e/defn DirectoryExplorer []
  (dom/style (dom/text css))
  (dom/div (dom/props {:class "DirectoryExplorer"})
    (let [[fs-rel-path] router/route]
      (if-not fs-rel-path
        (router/ReplaceState! ['. [""]])
        (router/pop
          (e/server
            (binding [base-path (fs/absolute-path "./")]
              (let [x (if-not fs-rel-path ; workaround glitch on tutorial navigate (nested router interaction)
                        {} (clojure.java.io/file base-path (check fs-rel-path)))]
                (Dir x)))))))))

(comment
  (def m (datafy (clojure.java.io/file (fs/absolute-path "./"))))
  (def xs (nav m ::fs/children ((::fs/children m))))
  (def xs2 ((treelister (fn [m] (::fs/children m)) #(includes-str? (::fs/name %) %2) xs) ""))
  (count (seq xs))
  (def qs (take 10 xs))
  (first qs))

(def css "
/* Scroll machinery */
.DirectoryExplorer .Viewport { height: 100%; overflow-x:hidden; overflow-y:auto; }
.DirectoryExplorer table { display: grid; }
.DirectoryExplorer table tr { display: contents; visibility: var(--visibility); }
.DirectoryExplorer table td { grid-row: var(--order); }

/* fullscreen, except in tutorial mode */
.Tutorial > .DirectoryExplorer fieldset { height: 30em; } /* max-height doesn't work - fieldset quirk */
:not(.Tutorial) > .DirectoryExplorer fieldset { position:fixed; top:0em; bottom:0; left:0; right:0; }

/* Cosmetic styles */
.DirectoryExplorer fieldset { padding: 0; padding-left: 0.5em; background-color: white; }
.DirectoryExplorer legend { margin-left: 1em; font-size: larger; }
.DirectoryExplorer legend > input[type=text] { vertical-align: middle; }
.DirectoryExplorer table { grid-template-columns: auto 6em 5em 3em; }
.DirectoryExplorer table td { height: 24px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.DirectoryExplorer table tr:hover td { background-color: #ddd; }
.DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }")