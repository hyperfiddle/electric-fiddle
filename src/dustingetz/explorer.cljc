(ns dustingetz.explorer
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            contrib.str
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [dustingetz.gridsheet3 :as gridsheet :refer [Explorer]]))

(def unicode-folder "\uD83D\uDCC2") ; 📂

(e/defn Render-cell [m a] ; server
  (let [v (a m)]
    #_(dom/text (pr-str v))
    (case a
      ; diff corruption but doesn't crash
      ::fs/name (case (::fs/kind m)
                  ::fs/dir (let [absolute-path (::fs/absolute-path m)]
                             (r/link absolute-path (dom/text v)))
                  (::fs/other ::fs/symlink ::fs/unknown-kind) (e/client (dom/text v))
                  (dom/text v))
      ::fs/modified (e/client (some-> v .toLocaleDateString dom/text))

      ; unstable, crashes quickly
      #_#_::fs/kind (case (::fs/kind m)
                      ::fs/dir (dom/text unicode-folder)
                      (e/client (some-> v name dom/text)))
      (dom/text (e/client (str v))))))

(e/defn Dir [x]
  (e/server
    (let [m (datafy x)
          xs (nav m ::fs/children (::fs/children m))]
      (dom/h1 (dom/text (::fs/absolute-path m)))
      (Explorer
        (treelister xs ::fs/children #(contrib.str/includes-str? (::fs/name %) %2))
        {::dom/style {:height "calc((20 + 1) * 24px)"}
         ::gridsheet/page-size 20
         ::gridsheet/row-height 24
         ::gridsheet/Format Render-cell
         ::gridsheet/columns [::fs/name ::fs/modified ::fs/size ::fs/kind]
         ::gridsheet/grid-template-columns "auto 8em 5em 3em"}))))

(e/defn DirectoryExplorer []
  (dom/link (dom/props {:rel :stylesheet, :href "user/gridsheet-optional.css"}))
  (dom/div (dom/props {:class "user-gridsheet-demo"})
    #_(binding [r/build-route (fn [[self state local-route] local-route']
                                ; root local links through this entrypoint
                              `[DirectoryExplorer ~state ~local-route'])])
    (let [[self s route] r/route
          fs-path (e/server (or route (fs/absolute-path "./src/")))]
      (r/focus [1] ; focus state slot, todo: fix IndexOutOfBounds exception
        (e/server
          (Dir (clojure.java.io/file fs-path)))))))

(comment
  (def m (datafy (clojure.java.io/file (fs/absolute-path "./"))))
  (def xs (nav m ::fs/children (::fs/children m)))
  (def xs ((treelister xs ::fs/children #(contrib.str/includes-str? (::fs/name %) %2)) ""))
  (count (seq xs))

  )

; Improvements
; Native search
; lazy folding/unfolding directories (no need for pagination)
; forms (currently table hardcoded with recursive pull)
