(ns dustingetz.london-talk-2024.webview-generic
  (:require #?(:clj [dustingetz.teeshirt-orders-datascript :refer [ensure-db!]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.typeahead :refer [Typeahead]]
            [dustingetz.london-talk-2024.webview-concrete :refer [Teeshirt-orders Genders Shirt-sizes]]))

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
        (dom/td (Typeahead gender
                  (e/fn Options [search] (Genders db search))
                  #_(e/fn OptionLabel [x] (pr-str x))))
        (dom/td (Typeahead shirt-size
                  (e/fn Options [search] (Shirt-sizes db gender search))
                  #_(e/fn OptionLabel [x] (pr-str x))))))))

(e/defn WebviewGeneric []
  (e/server
    (let [db (ensure-db!)
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))]
      (GenericTable
        (e/Partial Teeshirt-orders db search)
        (e/Partial Row db)))))