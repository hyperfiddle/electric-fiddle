(ns dustingetz.london-talk-2024.webview-scroll-dynamic
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
    (let [!e (d/entity db id)
          email (-> !e :order/email)
          gender (-> !e :order/gender :db/ident)
          shirt-size (-> !e :order/shirt-size :db/ident)]
      {:db/id (e/fn [] (dom/td (dom/text id)))
       :order/email (e/fn [] (dom/td (dom/text email)))
       :order/gender (e/fn [] (dom/td (Typeahead gender
                                        (e/fn Options [search] (Genders db search))
                                        #_(e/fn OptionLabel [x] (pr-str x)))))
       :order/shirt-size (e/fn [] (dom/td (Typeahead shirt-size
                                            (e/fn Options [search] (Shirt-sizes db gender search))
                                            #_(e/fn OptionLabel [x] (pr-str x)))))})))

(e/defn GenericTable [colspec Row xs]
  (e/for [x xs]
    (let [m (dom/tr (Row x))]
      (e/for [k colspec]
        (e/call (get m k))))))

(e/defn TableScrollFixedCounted
  [Table xs #_& {:keys [record-count row-height]}]
  (dom/props {:style {:overflow-y "auto"}}) ; no wrapper div! attach to natural container
  (let [[offset limit] (Scroll-window row-height record-count dom/node {:overquery-factor 1})]
    (dom/table (dom/props {:style {:position "relative" :top (str (* offset row-height) "px")}})
      (Table (second (Spool record-count xs offset limit))))
    (dom/div (dom/props {:style {:height (str (* row-height record-count) "px")}}))))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
#?(:cljs (def !sort-key (atom [:order/email])))

(declare css)
(e/defn WebviewScrollDynamic []
  (dom/style (dom/text css))
  (let [db (e/server (e/watch (ensure-db!)))
        colspec (e/server (e/diff-by identity (e/client (e/watch !colspec))))
        search (Input* "")]
    (dom/div (dom/props {:class "UserViewport"})
      (let [xs (e/server (e/Offload #(teeshirt-orders db search [:order/email])))]
        (e/server ; caller chooses topology, perf is about the same
          (TableScrollFixedCounted
            (e/Partial GenericTable colspec (e/Partial Row db))
            xs
            {:record-count (e/server (count xs))
             :row-height 25}))))))

; Requires css {box-sizing: border-box;}
(def css "
.UserViewport { position: fixed; top: 3em; bottom:0; left:0; right:0; }
.UserViewport table { display: grid; grid-template-columns: 4em 12em 10em auto; }
.UserViewport table tr { display: contents; }
")