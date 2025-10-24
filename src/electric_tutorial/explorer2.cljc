(ns electric-tutorial.explorer2
  (:require [clojure.datafy :refer [datafy]]
            [clojure.math :as math]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-css3 :as css]
            [hyperfiddle.electric-dom3-props :as props]
            [hyperfiddle.electric-forms5 :refer [Input*]]
            [dustingetz.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [dustingetz.treelister3 :refer [treelist]]
            [dustingetz.str :refer [includes-str?]]
            [hyperfiddle.router5 :as router]
            [leonoel.util :as lu])
  #?(:clj (:import (java.io File))))

#?(:clj (defn hidden-or-node-modules [^File x] (or (fs/file-hidden? x) (= "node_modules" (.getName x)))))

#?(:clj (defn fs-tree-seq [^File x search]
          ; linear search over 10k+ records is too slow w/o a search index, so remove node_modules and .git
          (->> x
            (treelist ; bug - elides empty folders (which you want only when search is not "")
              (fn children [x]
                (when-not (hidden-or-node-modules x) (map-indexed vector (fs/dir-list x))))
              (fn keep? [x] (and (not (hidden-or-node-modules x)) (includes-str? (.getName x) search)))))))

#?(:cljs (defn scroll-top [element]
           (.-scrollTop element)))

(def unicode-folder "\uD83D\uDCC2") ; 📂
(e/declare base-path)

(lu/defrule css
  (lu/define
    :height "100%"
    :min-height "calc(var(--min-row-count, 3) * var(--row-height))"
    :overflow-x "hidden"
    :overflow-y "auto"
    :contain    "size")
  (lu/select " > table"
    (lu/define
      :border-spacing "0"
      :margin-top     "calc(var(--skipped-before) * var(--row-height))"
      :margin-bottom  "calc(var(--skipped-after) * var(--row-height))"
      :width          "100%")
    (lu/select " > tr"
      (lu/define :height "var(--row-height)")
      (lu/select ".odd"   (lu/define :background-color "white"))
      (lu/select ".even"  (lu/define :background-color "#f2f2f2"))
      (lu/select ":hover" (lu/define :background-color "#ddd"))
      (lu/select " > td"
        (lu/define
          :padding-left "calc(var(--tab, 0) * 1em)"
          :overflow "hidden"
          :white-space "nowrap"
          :text-overflow "ellipsis")))))

(e/defn Row [index record]
  (e/server
    (let [[p f] record
          tab (count p)
          ?x (datafy f)   ; should be offloaded
          n (::fs/name ?x)
          k (::fs/kind ?x)
          m (::fs/modified ?x)
          s (::fs/size ?x)
          dir? (= ::fs/dir k)
          path (and dir? (fs/relativize-path base-path (::fs/absolute-path ?x)))]
      (e/client
        (dom/tr
          (props/set-class! dom/node (["even" "odd"] (mod index 2)))
          (dom/td
            (css/set-property dom/node :--tab tab)
            (if (and dir? path)
              (router/link ['.. [path]] (dom/text n))
              (dom/text n)))
          (dom/td
            (dom/text (.toLocaleDateString m)))
          (dom/td
            (dom/text (str s)))
          (dom/td
            (dom/text (if dir? unicode-folder (name k)))))))))

(e/defn TableScroll [overquery row-height record-count records]
  (e/client
    (dom/div
      (props/set-class! dom/node (e/input css))
      (css/set-property dom/node :--row-height (str row-height "px"))
      (let [total-height (* row-height record-count)
            viewport-height (e/input (lu/height dom/node))
            scroll-top (e/input (lu/property dom/node scroll-top lu/listen {"scroll" (js-obj "passive" true)}))
            scroll-pos (min 1 (/ scroll-top (- total-height viewport-height)))
            half-size (/ (* (+ 0.5 overquery) viewport-height) row-height)
            query-center (* scroll-pos record-count)
            query-start (max 0 (math/floor (- query-center half-size)))
            query-end (min record-count (math/ceil (+ query-center half-size)))]
        (dom/table
          (css/set-property dom/node :--skipped-before query-start)
          (css/set-property dom/node :--skipped-after (- record-count query-end))
          (e/for [i (lu/Range query-start query-end)]
            (e/server
              (when-some [record (nth records i nil)]       ;; d-glitch workaround
                (Row i record)))))))))

(def overquery 1)   ;; rows to pre-fetch before and after the rendered window, relative to the visible row count
(def row-height 24)

(e/defn Dir [?x]
  (e/server
    (let [!search (atom "") search (e/watch !search)
          xs! (ex/Offload-latch #(vec (when ?x (fs-tree-seq ?x search)))) ; glitch
          n (count xs!)]
      (dom/fieldset
        (e/client
          (props/set-class! dom/node "explorer"))
        (dom/legend (dom/text (fs/file-absolute-path ?x) " ")
          (do (reset! !search (e/client (Input* ""))) nil) (dom/text " (" n " items)"))
        (TableScroll overquery row-height n xs!)))))

(e/defn DirectoryExplorer2 []
  (dom/style (dom/text "
.explorer {height: 100%; overflow: clip}
*:has(.explorer) {height: 100%}
body:has(.explorer) {max-height: 100dvh; box-sizing: border-box}
  "))
  (e/server
    (let [[fs-rel-path] router/route]
      (if-not fs-rel-path
        (router/ReplaceState! ['. [""]])
        (router/pop
          (e/server
            (binding [base-path (fs/absolute-path "./")]
              (Dir (when (some? fs-rel-path) ; glitch on navigate
                     (clojure.java.io/file base-path fs-rel-path))))))))))