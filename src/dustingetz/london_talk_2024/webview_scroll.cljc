(ns dustingetz.london-talk-2024.webview-scroll
  (:require #?(:clj [datascript.core :as d])
            [electric-tutorial.typeahead :refer [Typeahead]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.electric-forms0 :refer [Input*]]
            [hyperfiddle.electric-scroll0 :refer [TableScrollFixedCounted]]
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
        (dom/td (Typeahead gender (e/fn [search] (Genders db search))))
        (dom/td (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search))))))))

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