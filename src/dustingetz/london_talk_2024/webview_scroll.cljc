(ns dustingetz.london-talk-2024.webview-scroll
  (:require #?(:clj [datascript.core :as d])
            [electric-tutorial.typeahead :refer [Typeahead]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window Spool]]
            #?(:clj [dustingetz.teeshirt-orders-datascript-dustin :refer
                     [teeshirt-orders genders shirt-sizes]])
            #?(:clj [dustingetz.teeshirt-orders-datascript-dustin-large :refer [ensure-db!]])))

(e/defn Genders [db search]
  (e/server (e/diff-by identity (e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity (e/Offload #(shirt-sizes db gender search)))))

(e/defn Row [db id]
  (e/server
    (let [!e         (d/entity db id)
          email      (-> !e :order/email)
          gender     (-> !e :order/gender :db/ident)
          shirt-size (-> !e :order/shirt-size :db/ident)]
      (dom/tr
        (dom/td (dom/text id) (dom/props {:style {:background-color (if (zero? (mod id 10)) "red")}}))
        (dom/td (dom/text email))
        (dom/td (Typeahead gender (e/fn [search] (Genders db search))))
        (dom/td (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search))))))))

#_
(e/defn TableScrollFixedCounted [colspec Query Row] ; todo remove all organic layout like studio
  (dom/style (dom/text ".TableScrollFixedCounted tr { display:grid; grid-template-columns: 4em 15em min-content min-content; grid-column: 1 / f-1; }"))
  (dom/div (dom/props {:class "TableScrollFixedCounted"
                       :style {:overflowX "hidden" :overflowY "auto" ; Requires css {box-sizing: border-box;}
                               :position "fixed" :top "0" :bottom "0" :left "0" :right "0"}})
    (let [row-height 25 ; todo relative measurement (note: browser zoom impacts px height)
          !record-count (e/client (atom 0)) record-count (e/client (e/watch !record-count))
          o-l #_[offset limit] (Scroll-window row-height record-count dom/node 1)
          offset (e/client (nth o-l 0))
          limit (e/client (nth o-l 1))]
      (dom/table
        #_(dom/props ; uncomment for quantized scroll
            (e/client {:style {:position "fixed" :transform (str "translate(0," (- (* offset row-height)) "px)")}}))
        (let [[record-count indexed-page] (Query :offset offset :limit limit)] ; neutral
          (e/client (reset! !record-count record-count))
          (e/for [[i x] indexed-page]
            (dom/tr (dom/props {:style {:height (str row-height "px") ; todo shouldn't need this
                                        :position "absolute" :top (str (* i row-height) "px")}})
              (let [m (Row x)]
                (e/for [k colspec]
                  (dom/td
                    (e/call (get m k)))))))))
      (dom/div (dom/props {:style {:height (str (* row-height record-count) "px")}})))))

(e/defn TableScrollFixedCounted
  [xs! TableBody #_& {:keys [record-count row-height overquery-factor]
                      :or {overquery-factor 1}}]
  (dom/props {:style {:overflow-y "auto"}}) ; no wrapper div! attach to natural container

  (let [record-count (or record-count (count xs!))
        [offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor overquery-factor})
        xs (second (Spool record-count xs! offset limit))] ; site neutral, caller chooses
    (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
      (TableBody xs)) ; no row markup/style requirement
    (dom/div (dom/props {:style {:height (str (* row-height (- record-count limit)) "px")}}))))

(declare css)
(e/defn WebviewScroll []
  (dom/style (dom/text css))
  (let [db (e/server (e/watch (ensure-db!)))
        search (Input* "")]
    (dom/div (dom/props {:class "UserViewport"})
      (let [xs (e/server (e/Offload #(teeshirt-orders db search [:order/email])))]
        (e/server ; caller chooses topology, perf is about the same
          (TableScrollFixedCounted xs
            (e/fn TableBody [xs] (e/for [x xs] (Row db x)))
            {:record-count (e/server (count xs))
             :row-height 25}))))))

; Requires css {box-sizing: border-box;}
(def css "
.UserViewport { position: fixed; top: 3em; bottom:0; left:0; right:0; }
.UserViewport table { display: grid; grid-template-columns: 4em 12em 10em auto; }
.UserViewport table tr { display: contents; }
")