(ns dustingetz.london-talk-2024.webview-scroll
  (:require #?(:clj [datascript.core :as d])
            [electric-tutorial.typeahead :refer [Typeahead]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [Scroll-window Spool]]
            #?(:clj [models.teeshirt-orders-datascript-dustin :refer
                     [teeshirt-orders genders shirt-sizes]])
            #?(:clj [models.teeshirt-orders-datascript-dustin-large :refer [ensure-db!]])))

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
        (dom/td #_(dom/text gender) (Typeahead gender (e/fn [search] (Genders db search))))
        (dom/td #_(dom/text shirt-size) (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search))))))))

(e/defn TableScrollFixedCounted
  [xs #_& {:keys [Row record-count row-height]}]
  (dom/props {:style {:overflow-y "auto"}}) ; attach to user container
  (let [[offset limit] (Scroll-window row-height record-count dom/node)]
    (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
      (e/for [[i x] (Spool record-count xs offset limit)] ; site neutral, caller chooses
        (Row x))) ; no markup/style requirement!
    (dom/div (dom/props {:style {:height (str (* row-height record-count) "px")}}))))

(declare css)
(e/defn WebviewScroll []
  (dom/style (dom/text css))
  (let [db (e/server (e/watch (ensure-db!)))
        search (Input* "")]
    (dom/div (dom/props {:class "Viewport"})
      (let [xs (e/server (e/Offload #(teeshirt-orders db search [:order/email])))]
        (e/server ; caller chooses topology, perf is about the same
          (TableScrollFixedCounted xs
            {:Row (e/Partial Row db)
             :record-count (e/server (count xs))
             :row-height 25}))))))

; Requires css {box-sizing: border-box;}
(def css "
.Viewport { position: fixed; top: 3em; bottom:0; left:0; right:0; }
table { display: grid; grid-template-columns: 4em 12em 10em auto; }
table tr { display: contents; }
")