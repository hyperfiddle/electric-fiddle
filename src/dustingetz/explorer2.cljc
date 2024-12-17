(ns dustingetz.explorer2
  (:require [clojure.datafy :refer [datafy]]
            [clojure.core.protocols :refer [nav]]
            #?(:clj clojure.java.io)
            [contrib.data :refer [treelister]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [contrib.str :refer [includes-str?]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :as scroll :refer [Scroll-window]]
            [hyperfiddle.router3 :as r]
            [contrib.debug :as dbg]))

(def unicode-folder "\uD83D\uDCC2") ; 📂

(e/defn Render-cell [m a]
  (let [v (a m)]
    (case a
      ::fs/name (case (::fs/kind m)
                  ::fs/dir (let [absolute-path (::fs/absolute-path m)]
                             (r/link #_['.. 0 absolute-path] ['.. [absolute-path ""]] ; discard search
                               (dom/text v)))
                  #_#_(::fs/other ::fs/symlink ::fs/unknown-kind) (dom/text v) ; perf - reuse same text node
                  (dom/text v))
      ::fs/modified (dom/text (e/client (some-> v .toLocaleDateString)))
      ::fs/kind (dom/text (case (::fs/kind m)
                            ::fs/dir unicode-folder
                            (e/client (some-> v name))))
      (dom/text (e/client (str v))))))

;; optimized, `dom/text` doesn't re-mount so much
;; commented out so comparison with explorer (with spool) is fair
#_(e/defn Render-cell [m a]
  (let [v (a m)
        txt (if (and (= ::fs/name a) (= ::fs/dir (::fs/kind m)))
              (let [absolute-path (::fs/absolute-path m)]
                (r/link #_['.. 0 absolute-path] ['.. [absolute-path ""]] ; discard search
                  (dom/text v))
                nil)
              (case a
                ::fs/name v
                ::fs/modified (e/client (some-> v .toLocaleDateString))
                ::fs/kind (case (::fs/kind m) ::fs/dir unicode-folder #_else (e/client (some-> v name)))
                #_else (e/client (str v))))]
    (some-> txt dom/text)))

(e/defn Unglitch [x]
  (let [[value clock] (e/with-cycle [[p c] [::init 0]]
                        [x (if (= p x) c (inc c))])]
    (if (= clock (e/server (identity clock)))
      value
      (e/amb))))

(e/defn Or [x y] (e/Reconcile (if (zero? (e/Count x)) y x)))

(e/defn TableScroll [xs! #_& {:keys [row-height overquery-factor record-count] :as props}]
  (dom/div (dom/props {:class "Viewport"})
    (let [[offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})]
      (dom/table
        (dom/props {:style {:position "sticky", :top "0"}})
        ;; same as explorer (with Spool), absolute index sent from server together with data
        ;; same performance and behavior
        #_(e/for [[i [tab x]] (e/server
                                ((fn [i] [i (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children)])
                                 (scroll/IndexRing limit offset)))]
            (dom/tr
              (dom/props {:style {:--order i}})
              (dom/td (Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
              (dom/td (Render-cell x ::fs/modified))
              (dom/td (Render-cell x ::fs/size))
              (dom/td (Render-cell x ::fs/kind))))
        ;; e/for on server doesn't make a difference. But body has to be on client,
        ;; otherwise under latency one can observe extra round-trips due to links
        #_(e/server
            (e/for [i (scroll/IndexRing limit offset)]
              (let [[tab x] (e/server (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children))]
                (e/client
                  (dom/tr
                    (dom/props {:style {:--order i}})
                    (dom/td (Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
                    (dom/td (Render-cell x ::fs/modified))
                    (dom/td (Render-cell x ::fs/size))
                    (dom/td (Render-cell x ::fs/kind)))))))
        ;; absolute index on client, data comes later from server
        ;; means order changes immediately, data afterwards
        ;; behavioral differences visible under high latency:
        ;; - scrolls immediately. Above versions lag so much user feels like scroll wasn't received
        ;; - scrolled data is stale until new data arrives. One can add visibility:hidden
        ;;   to the scrolled rows, then the rows look empty and the glitch is gone.
        ;;   But this damages performance under no latency.
        (e/for [i (scroll/IndexRing limit offset)]
          (let [[tab x] (e/server (update (nth xs! i) 1 dissoc :contrib.datafy-fs/children)) ; children are File objects
                ;; visible? (Or ({} (Unglitch i) "visible") "hidden")
                ]
            (dom/tr
              (dom/props {:style {:--order i, #_ #_:--visibility visible?}})
              (dom/td (Render-cell x ::fs/name) (dom/props {:style {:padding-left (-> tab (* 15) (str "px"))}}))
              (dom/td (Render-cell x ::fs/modified))
              (dom/td (Render-cell x ::fs/size))
              (dom/td (Render-cell x ::fs/kind))))))
      (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}})))))

(def css "
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; position:fixed; top:8em; bottom:0; left:0; right:0; }
.DirectoryExplorer table { position: relative; /* position: sticky; top:0; */ }
.DirectoryExplorer table { display: grid; grid-template-columns: auto 8em 8em 10em; }
.DirectoryExplorer table tr { display: contents; }
.DirectoryExplorer table td { height: 24px; }
.DirectoryExplorer table tr td { grid-row: var(--order); visibility: var(--visibility); }
/* .DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; } */
.DirectoryExplorer table tr:hover td { background-color: #ddd; }")

(e/defn Dir [x]
  (let [m (e/server (datafy x))
        xs! (e/server (vec ((treelister ::fs/children #(includes-str? (::fs/name %) %2)
                              (nav m ::fs/children (::fs/children m))) "")))
        cnt (e/server (count xs!))]
    (dom/h1 (dom/text (e/server (::fs/absolute-path m)) " (" cnt " items)"))
    (TableScroll xs! {:row-height 24 :overquery-factor 1, :record-count cnt})))

(e/defn DirectoryExplorer2 []
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
