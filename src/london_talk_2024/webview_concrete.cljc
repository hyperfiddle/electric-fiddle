(ns london-talk-2024.webview-concrete
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.typeahead :refer [Typeahead]]))

(e/defn Tap-diffs [x] (println 'diff (pr-str (e/input (e/pure x)))) x)

(e/defn Genders [db search]
  (e/server (e/diff-by identity (e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity (e/Offload #(shirt-sizes db gender search)))))

(e/defn Teeshirt-orders [db search & [sort-key]]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search sort-key))))) ; e.g. [9 10 11]

(e/defn WebviewConcrete []
  (e/server
    (let [db (e/watch conn)
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))
          ids (Teeshirt-orders db search)]
      (dom/table
        (e/for [id ids]
          (dom/tr
            (let [!e (d/entity db id)
                  email (-> !e :order/email)
                  gender (-> !e :order/gender :db/ident)
                  shirt-size (-> !e :order/shirt-size :db/ident)]
              (dom/td (dom/text id))
              (dom/td (dom/text email))
              (dom/td (e/client ; fixme
                        (Typeahead gender (e/fn [search] (Genders db search)))))
              (dom/td (e/client (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search))))))))))))