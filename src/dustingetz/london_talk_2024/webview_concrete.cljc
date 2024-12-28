(ns dustingetz.london-talk-2024.webview-concrete
  (:require #?(:clj [dustingetz.teeshirt-orders-datascript-dustin :refer
                     [ensure-db! teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.typeahead :refer [Typeahead]]))

(e/defn Genders [db search]
  (e/server (e/diff-by identity (e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity (e/Offload #(shirt-sizes db gender search)))))

(e/defn Teeshirt-orders [db search & {:keys [sort-key]}]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search sort-key))))) ; e.g. [9 10 11]

(e/defn WebviewConcrete []
  (e/server
    (let [db (e/watch (ensure-db!))
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))
          ids (Teeshirt-orders db search)]
      (dom/table
        (e/for [id ids]
          (dom/tr
            (let [!e (d/entity db id)
                  gender (-> !e :order/gender :db/ident)]
              (dom/td (dom/text id))
              (dom/td (dom/text (:order/email !e)))
              (dom/td (Typeahead gender
                        (e/fn Options [search] (Genders db search))
                        #_(e/fn OptionLabel [x] (pr-str x))))
              (dom/td (Typeahead (-> !e :order/shirt-size :db/ident)
                        (e/fn Options [search] (Shirt-sizes db gender search))
                        #_(e/fn OptionLabel [x] (pr-str x)))))))))))