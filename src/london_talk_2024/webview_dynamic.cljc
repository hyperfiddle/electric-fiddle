(ns london-talk-2024.webview-dynamic
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.typeahead :refer [Typeahead]]
            [london-talk-2024.webview-concrete :refer [Teeshirt-orders Genders Shirt-sizes Tap-diffs]]))

(e/defn GenericTable [colspec Query Row]
  (let [ids (Query)]
    (dom/table
      (e/for [id ids]
        (dom/tr
          (let [m (Row id)]
            (e/for [k colspec]
              (dom/td
                (e/call (get m k))))))))))

(e/defn Row [db id]
  (e/server
    (let [!e         (d/entity db id)
          email      (-> !e :order/email)
          gender     (-> !e :order/gender :db/ident)
          shirt-size (-> !e :order/shirt-size :db/ident)]
      {:db/id            (e/fn [] (dom/text id))
       :order/email      (e/fn [] (dom/text email))
       :order/gender     (e/fn [] (e/client (Typeahead gender (e/fn [search] (Genders db search)))))
       :order/shirt-size (e/fn [] (e/client (Typeahead shirt-size (e/fn [search] (Shirt-sizes db gender search)))))})))


#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
#?(:cljs (def !sort-key (atom [:order/email])))

(e/defn WebviewDynamic []
  (e/server
    (let [db (e/watch conn)
          colspec (e/client (e/diff-by identity (e/watch !colspec)))
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))]
      (GenericTable
        colspec
        (e/Partial Teeshirt-orders db search (e/client (e/watch !sort-key)))
        (e/Partial Row db)))))

(comment
  (reset! !colspec [:db/id])
  (swap! !colspec conj :order/email)
  (swap! !colspec conj :order/gender)
  (swap! !colspec conj :order/shirt-size)

  (reset! !sort-key [:db/id])
  (reset! !sort-key [:order/shirt-size :db/ident])
  )