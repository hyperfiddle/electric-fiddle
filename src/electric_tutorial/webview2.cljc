(ns electric-tutorial.webview2
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer
                     [conn teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.typeahead :refer [Typeahead]]))

(e/defn Genders [db search]
  (e/server (e/diff-by identity (e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity (e/Offload #(shirt-sizes db gender search)))))

(e/defn Teeshirt-orders [db search & [sort-key]]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search sort-key)))))

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
       :order/gender     (e/fn [] (Typeahead gender
                                    (e/fn Options [search] (Genders db search))
                                    (e/fn OptionLabel [x] (pr-str x))))
       :order/shirt-size (e/fn [] (Typeahead shirt-size
                                    (e/fn Options [search] (Shirt-sizes db gender search))
                                    (e/fn OptionLabel [x] (pr-str x))))})))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
#?(:cljs (def !sort-key (atom [:order/email])))

(e/defn Webview2 []
  (dom/props {:class "webview"})
  (e/server
    (let [db (e/watch conn)
          colspec (e/client (e/diff-by identity (e/watch !colspec)))
          search (e/client (dom/input (dom/On "input" #(-> % .-target .-value) "")))]
      (GenericTable
        colspec
        (e/Partial Teeshirt-orders db search (e/client (e/watch !sort-key)))
        (e/Partial Row db)))))

(comment
  ; todo align grid css to number of columns
  (reset! !colspec [:db/id])
  (swap! !colspec conj :order/email)
  (swap! !colspec conj :order/gender)
  (swap! !colspec conj :order/shirt-size)

  (reset! !sort-key [:db/id])
  (reset! !sort-key [:order/shirt-size :db/ident]))