(ns electric-tutorial.webview2
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

(e/defn Teeshirt-orders [db search & [sort-key]]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search sort-key)))))

(e/defn GenericTable [cols Query Row]
  (let [ids (Query)] ; server query
    (dom/table
      (e/for [id ids]
        (dom/tr
          (let [m (Row id)]
            (e/for [k cols]
              (dom/td
                (e/call (get m k))))))))))

(e/defn Row [db id]
  (let [!e         (e/server (e/Offload #(d/entity db id)))
        email      (e/server (-> !e :order/email))
        gender     (e/server (-> !e :order/gender :db/ident))
        shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
    {:db/id            (e/fn [] (dom/text id))
     :order/email      (e/fn [] (dom/text email))
     :order/gender     (e/fn [] (Typeahead gender
                                  (e/fn Options [search] (Genders db search))
                                  #_(e/fn OptionLabel [x] (pr-str x))))
     :order/shirt-size (e/fn [] (Typeahead shirt-size
                                  (e/fn Options [search] (Shirt-sizes db gender search))
                                  #_(e/fn OptionLabel [x] (pr-str x))))}))


#?(:cljs (def !sort-key (atom [:order/email])))

(declare css)
(e/defn Webview2 []
  (e/client
    (dom/style (dom/text css))
    (let [db (e/server (ensure-db!))
          colspec (e/amb :db/id :order/email :order/gender :order/shirt-size) ; reactive tuple
          search (dom/input (dom/On "input" #(-> % .-target .-value) ""))]
      (GenericTable
        colspec
        (e/Partial Teeshirt-orders db search (e/client (e/watch !sort-key)))
        (e/Partial Row db)))))

(comment
  (reset! !sort-key [:db/id])
  (reset! !sort-key [:order/shirt-size :db/ident]))

(def css "
.user-examples-target table { display: grid; column-gap: 1ch; }
.user-examples-target tr { display: contents; }
.user-examples-target table {grid-template-columns: repeat(4, max-content);}
.user-examples-target { padding-bottom: 5em; /* room for picklist options */}")