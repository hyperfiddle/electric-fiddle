(ns dustingetz.london-talk-2024.webview-scroll-dynamic
  (:require #?(:clj [datascript.core :as d])
            [electric-tutorial.typeahead :refer [Typeahead]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [TableScrollFixedCounted]]
            [dustingetz.london-talk-2024.webview-scroll :refer [Genders Shirt-sizes]]
            #?(:clj [models.teeshirt-orders-datascript-dustin :refer [teeshirt-orders]])
            #?(:clj [models.teeshirt-orders-datascript-dustin-large :refer [ensure-db!]])))

(e/defn Row [db id]
  (dom/tr
    (e/server
      (let [!e (d/entity db id)
            email (-> !e :order/email)
            gender (-> !e :order/gender :db/ident)
            shirt-size (-> !e :order/shirt-size :db/ident)]
        {:db/id (e/fn [] (dom/td (dom/text id)))
         :order/email (e/fn [] (dom/td (dom/text email)))
         :order/gender (e/fn [] (dom/td (Typeahead gender (e/fn [search] (Genders db search)))))
         :order/shirt-size (e/fn [] (dom/td (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search)))))}))))

(e/defn GenericTable [colspec Row xs]
  (e/for [x xs]
    (let [m (Row x)]
      (e/for [k colspec]
        (e/call (get m k))))))

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
          (TableScrollFixedCounted xs
            (e/Partial GenericTable colspec (e/Partial Row db))
            {:record-count (e/server (count xs))
             :row-height 25}))))))

; Requires css {box-sizing: border-box;}
(def css "
.UserViewport { position: fixed; top: 3em; bottom:0; left:0; right:0; }
.UserViewport table { display: grid; grid-template-columns: 4em 12em 10em auto; }
.UserViewport table tr { display: contents; }
")