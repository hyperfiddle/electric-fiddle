(ns electric-tutorial.webview-virtualscroll
  (:require
    #?(:clj [dustingetz.teeshirt-orders-datascript :refer [ensure-db! genders shirt-sizes teeshirt-orders]])
    [contrib.data :refer [clamp-left]]
    [datascript.core :as d]
    [electric-tutorial.typeahead :refer [Typeahead]]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.electric-scroll0 :refer [Scroll-window Tape]]
    [hyperfiddle.electric3 :as e]))

(e/declare db)

(e/defn Row [i x]
  (let [!e         (e/server (e/Offload #(d/entity db x)))
        email      (e/server (-> !e :order/email))
        gender     (e/server (-> !e :order/gender :db/ident))
        shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
    (dom/tr (dom/props {:style {:--order (inc i)} :data-row-stripe (mod i 2)})
      (dom/td (dom/text email))
      (dom/td (when gender
                (Typeahead (e/server (:db/ident (d/entity db gender)))
                  (e/server (fn [search] (genders db search)))
                  (e/fn Row [x] (dom/text (str x))))))
      (dom/td (when shirt-size
                (Typeahead (e/server (:db/ident (d/entity db shirt-size)))
                  (e/server (fn [search] (shirt-sizes db gender search)))
                  (e/fn Row [x] (dom/text (str x)))))))))

(def row-height 24)
(e/defn VirtualScroll [xs!]
  (let [row-count (e/server (count xs!))
        [offset limit] (Scroll-window row-height row-count dom/node {:overquery-factor 1})
        occluded-rows-height (clamp-left (* row-height (- row-count limit)) 0)]
    (dom/props {:style {:--offset offset :--row-height (str row-height "px")}})
    (dom/table
      (e/for [i (Tape offset limit)]
        (Row i (e/server (nth xs! i nil)))))
    (dom/div (dom/props {:style {:height (str occluded-rows-height "px")}}))))

(e/defn Teeshirt-orders [db search] (e/server (e/Offload #(teeshirt-orders db search))))

(declare css)
(e/defn OrderForm []
  (dom/style (dom/text css))
  (binding [db (e/server (ensure-db!))]
    (let [search (dom/input (dom/On "input" #(-> % .-target .-value) ""))
          xs! (Teeshirt-orders db search)]
      (dom/div (dom/props {:class "App"})
        (dom/div (dom/props {:class "Viewport"})
          (VirtualScroll xs!))))))

(def css "
.Viewport { height: 400px; overflow: hidden auto; }
.Viewport table { display: grid; grid-template-columns: 2fr 1fr 1fr; position: relative; top: calc(var(--offset)*var(--row-height)); }
.Viewport tr { display: contents; visibility: var(--visibility,visible); }
.Viewport td { grid-row: var(--order); height: var(--row-height); }
.Viewport td:has(.Typeahead) { overflow: visible; }
.Viewport tr[data-row-stripe='0'] td { background: #f2f2f2; }
.Viewport tr:hover td { background:#ddd; }
.Typeahead { position: relative; }
.Typeahead ul { position: absolute; z-index: 10; max-height: 200px; overflow-y: auto; }
.Typeahead ul { background: #fff; border: 1px solid #ccc; list-style: none; margin: 0; padding:0; }
.Typeahead li { font-size: small; padding: 2px 6px; cursor: pointer; }
.Typeahead li:hover { background:#ddd; }
")
