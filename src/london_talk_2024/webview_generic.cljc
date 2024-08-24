(ns london-talk-2024.webview-generic
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.typeahead :refer [Typeahead]]
            [london-talk-2024.webview-concrete :refer [Teeshirt-orders Genders Shirt-sizes Tap-diffs]]))

(e/defn GenericTable [Query Row]
  (let [ids (Query)]
    (dom/table
      (e/for [id ids]
        (dom/tr
          (Row id))))))

(e/defn Row [db id]
  (e/server
    (let [!e         (d/entity db id)
          email      (-> !e :order/email)
          gender     (-> !e :order/gender :db/ident)
          shirt-size (-> !e :order/shirt-size :db/ident)]
      (e/client
        (dom/td (dom/text id))
        (dom/td (dom/text email))
        (dom/td (Typeahead gender (e/fn [search] (Genders db search))))
        (dom/td (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search))))))))

(e/defn WebviewGeneric []
  (e/server
    (let [db (e/watch conn)
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))]
      (GenericTable
        (e/Partial Teeshirt-orders db search)
        (e/Partial Row db)))))