(ns dustingetz.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [dustingetz.gridsheet4 :as gridsheet :refer [Explorer]]))

(def unicode-folder "\uD83D\uDCC2") ; 📂

(e/defn Render-cell [m a] ; server
  (let [v (a m)]
    (case a
      ::fs/name (case (::fs/kind m)
                  ::fs/dir (let [absolute-path (::fs/absolute-path m)]
                             #_(r/link #_['.. 0 absolute-path] ['.. [absolute-path ""]]) ; discard search
                             (dom/text v #_#_" - " absolute-path))
                  (::fs/other ::fs/symlink ::fs/unknown-kind) (dom/text v)
                  (dom/text v))
      ::fs/modified (dom/text (e/client (some-> v .toLocaleDateString)))
      ::fs/kind (case (::fs/kind m)
                  ::fs/dir (dom/text unicode-folder)
                  (dom/text (e/client (some-> v name))))
      (dom/text (e/client (str v))))))

(e/defn Dir [x]
  (e/server
    (let [m (datafy x)]
      (dom/h1 (dom/text (::fs/absolute-path m)))
      (r/focus [1] ; search
        (Explorer
          (->> (nav m ::fs/children (::fs/children m))
            (treelister ::fs/children #(includes-str? (::fs/name %) %2)))
          {::dom/style {:height "calc((20 + 1) * 24px)"}
           ::gridsheet/page-size 20
           ::gridsheet/row-height 24
           ::gridsheet/Format Render-cell
           ::gridsheet/columns [::fs/name ::fs/modified ::fs/size ::fs/kind]
           ::gridsheet/grid-template-columns "auto 8em 5em 3em"})))))

(e/defn DirectoryExplorer []
  (dom/link (dom/props {:rel :stylesheet, :href "user/gridsheet-optional.css"}))
  (dom/div (dom/props {:class "user-gridsheet-demo"})
    (let [[fs-path search] r/route]
      (if-not fs-path (r/ReplaceState! ['. [(e/server (fs/absolute-path "./src/")) (or search "")]])
        (Dir (e/server (clojure.java.io/file fs-path)))))))

(comment
  (def m (datafy (clojure.java.io/file (fs/absolute-path "./"))))
  (def xs (nav m ::fs/children (::fs/children m)))
  (def xs ((treelister ::fs/children #(includes-str? (::fs/name %) %2) xs) ""))
  (count (seq xs))

  )

; Improvements
; Native search
; lazy folding/unfolding directories (no need for pagination)
; forms (currently table hardcoded with recursive pull)
