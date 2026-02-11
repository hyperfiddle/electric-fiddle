(ns dustingetz.talks.webview-concrete "http://localhost:8080/dustingetz.talks.webview-concrete!WebviewConcrete/"
  (:require #?(:clj [dustingetz.teeshirt-orders-datascript :refer
                     [ensure-db! teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.typeahead :refer [Typeahead]]))

(e/defn Genders [db search]
  (e/server (e/diff-by identity (e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity (e/Offload #(shirt-sizes db gender search)))))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity ; [9 10 11]
              (e/Offload #(teeshirt-orders db search)))))

(e/defn OrderForm [db search]
  (let [ids (Teeshirt-orders db search)]
    (dom/table
      (e/for [id ids]
        (e/server
          (let [!e (d/entity db id)
                gender (-> !e :order/gender :db/ident)]
            (e/client
              (dom/tr
                (dom/td (dom/text id))
                (dom/td (dom/text (e/server (:order/email !e))))
                (dom/td (Typeahead gender (e/fn [search] (Genders db search))))
                (dom/td (Typeahead (e/server (-> !e :order/shirt-size :db/ident))
                          (e/fn [search]
                            (Shirt-sizes db gender search))))))))))))

(e/defn WebviewConcrete []
  (e/client
    (let [db (e/server (ensure-db!))
          search (dom/input (dom/On "input" #(-> % .-target .-value) ""))]
      (OrderForm db search))))