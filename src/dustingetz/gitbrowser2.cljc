(ns dustingetz.gitbrowser2
  (:require [clojure.datafy :refer [datafy]]
            [contrib.data :refer [omit-keys]]
            [clojure.core.protocols :refer [nav]]
            [contrib.datafy-fs #?(:clj :as :cljs :as-alias) fs]
            [datagrid.datafy-git #?(:clj :as :cljs :as-alias) git]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window IndexRing]]
            [hyperfiddle.electric-forms0 :as forms :refer [Button! Service]]
            [missionary.core :as m]))

; strategy Explorer2 but with e/for on server
(e/defn TableScroll [xs! #_& {:keys [Row row-height]}]
  (dom/div (dom/props {:class "Viewport"})
    (let [record-count (e/server (count xs!))
          [offset limit] (Scroll-window row-height record-count dom/node {})]
      (e/amb
        (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
          (e/for [i (e/server (IndexRing limit offset))]
            (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
              (Row (e/server (datafy (nav xs! i (nth xs! i nil))))))))
        (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}}))))))

(e/defn Log [m]
  (dom/h1 (dom/text (e/server (-> m :repo datafy :dir datafy ::fs/absolute-path)) " — Git Browser"))
  (TableScroll (e/server (nav m :log (:log m)))
    {:row-height 24
     :Row (e/fn [m]
            (dom/td (dom/text (e/server (-> m :id git/short-commit-id))))
            (dom/td (dom/text (e/server (-> m :message))))
            (dom/td (dom/text (e/server (-> m :author))))
            (dom/td (dom/text (e/client (.toLocaleDateString (e/server (-> m :time)))))))}))

(e/defn Debug-cell [m]
  (e/server
    (let [seen [:ref-name :ref-name-short :commit :commit-short :object-id]]
      (dom/td (dom/text (pr-str (omit-keys m seen)))))))

(e/defn DeleteLocalRef [ref-name]
  (e/server
    (case (e/Task (m/sleep 1000))
      (do (println 'delete ref-name)
          ::forms/rejected))))

(e/defn Branches [m]
  (let [xs! (e/server (nav m :branches2 (:branches2 m)))]
    (dom/h1 (dom/text (e/server (count xs!)) " branches — Git Browser"))
    (TableScroll xs!
      {:row-height 24
       :Row (e/fn [{:keys [ref-type ref-name-short commit-short] :as m}]
              (e/amb
                (dom/td (dom/text ref-type))
                (dom/td (dom/text ref-name-short))
                (dom/td (dom/text commit-short))
                (dom/td (case ref-type
                          :local (Button! [`DeleteLocalRef (e/server (:ref-name m))]
                                   :label "delete")
                          (e/amb)))
                #_(Debug-cell m)))})))

(e/defn Page [page]
  (dom/div (dom/text "Nav: ")
    (r/link ['.. [:log]] (dom/text "log")) (dom/text " ")
    (r/link ['.. [:branches]] (dom/text "branches")) (dom/text " "))
  (let [m (e/server (datafy (git/load-repo "./")))]
    (binding [forms/effects* {`DeleteLocalRef DeleteLocalRef}]
      (Service
        (case page
          :log (Log m)
          :branches (Branches m)
          (e/amb))))))

(declare css)
(e/defn GitBrowser []
  (dom/style (dom/text css))
  (let [[page] r/route]
    (dom/div
      (dom/props {:class (str "DirectoryExplorer " (some-> page name))})
      (if-not page
        (r/ReplaceState! ['. [:log]])
        (r/pop (Page page))))))

(comment
  ; goal of datafy: memorize neither java api nor clojure api (?)
  ; just navigate api as data
  (def m (datafy (git/load-repo "./")))
  (nav m :log (:log m))
  (nav m :branches (:branches m)) ; nav brings the protocol along
  )

(def css "
.DirectoryExplorer .Viewport { overflow-x:hidden; overflow-y:auto; position:fixed; top:8em; bottom:0; left:0; right:0; }
.DirectoryExplorer table { display: grid; min-width: 0; }
.DirectoryExplorer table tr { display: contents; }
.DirectoryExplorer table td { height: 24px; }
.DirectoryExplorer table td { grid-row: var(--order); }
.DirectoryExplorer table td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.DirectoryExplorer table tr[data-row-stripe='0'] td { background-color: #f2f2f2; }
.DirectoryExplorer table tr:hover td { background-color: #ddd; }
.DirectoryExplorer.log table {grid-template-columns: 8em auto 8em 8em;}
.DirectoryExplorer.branches table {grid-template-columns: 8em auto 8em 8em;}
")


; Essay, key ideas
;   the record x is kept on the server
;   complex queries from the rows
;   the grid is still fast