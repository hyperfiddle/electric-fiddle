(ns london-talk-2024.webview-generic
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [london-talk-2024.typeahead :refer [Typeahead]]
            [london-talk-2024.webview-concrete :refer [Teeshirt-orders Genders Shirt-sizes]]))

(e/defn GenericTable [colspec Query Row]
  (e/client
    (dom/table
      (e/for [id ($ Query)]
        (dom/tr
          (let [m ($ Row id)]
            (e/for [k colspec]
              (dom/td
                ($ (get m k))))))))))

(e/defn Row [db id]
  (let [!e         (e/server (d/entity db id))
        email      (e/server (-> !e :order/email))
        gender     (e/server (-> !e :order/gender :db/ident))
        shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
    {:db/id            (e/fn [] (dom/text id))
     :order/email      (e/fn [] (dom/text email))
     :order/gender     (e/fn [] ($ Typeahead gender (e/fn [search] ($ Genders db search))))
     :order/shirt-size (e/fn [] ($ Typeahead shirt-size (e/fn [search] ($ Shirt-sizes db gender search))))}))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))

(e/defn WebviewGeneric []
  (e/client
    (let [db (e/server (e/watch conn))
          colspec (e/diff-by identity (e/watch !colspec))
          search (dom/input ($ dom/On "input" #(-> % .-target .-value) ""))]
      ($ GenericTable
        colspec
        ($ e/Partial Teeshirt-orders db search)
        ($ e/Partial Row db)))))

(comment
  (reset! !colspec [:db/id])
  (swap! !colspec conj :order/email)
  (swap! !colspec conj :order/gender)
  (swap! !colspec conj :order/shirt-size))