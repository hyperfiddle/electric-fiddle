(ns london-talk-2024.webview-generic
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Typeahead [v-id Options & [OptionLabel]]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"})
      (let [OptionLabel (or OptionLabel (e/fn [x] (pr-str x)))
            container dom/node
            !v-id (atom v-id) v-id (e/watch !v-id)]
        (dom/input
          (dom/props {:placeholder "Filter..."})
          (if-some [close! ($ e/Token ($ dom/On "focus"))]
            (let [search ($ dom/On "input" #(-> % .-target .-value) "")]
              (binding [dom/node container] ; portal
                (dom/ul
                  (e/for [id ($ Options search)]
                    (dom/li (dom/text ($ OptionLabel id))
                      ($ dom/On "click" (fn [e]
                                          (doto e (.stopPropagation) (.preventDefault))
                                          (reset! !v-id id) (close!))))))))
            (dom/props {:value ($ OptionLabel v-id)}))) ; controlled only when not focused
        v-id))))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity ($ e/Offload #(teeshirt-orders db search)))))

(e/defn Genders [db search]
  (e/server (e/diff-by identity ($ e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity ($ e/Offload #(shirt-sizes db gender search)))))

(e/defn SearchInput []
  (dom/input (dom/props {:placeholder "Filter..."})
    ($ dom/On "input" #(-> % .-target .-value) "")))

(e/defn GenericTable [colspec Query Record]
  (e/client
    (dom/table
      (e/for [id ($ Query)]
        (dom/tr
          (let [m ($ Record id)]
            (e/for [col colspec]
              (dom/td
                ($ (get m col))))))))))

(e/defn Record [db id]
  (let [!e (e/server (d/entity db id))
        email (e/server (-> !e :order/email))
        gender (e/server (-> !e :order/gender :db/ident))
        shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
    {:db/id (e/fn [] (dom/text id))
     :order/email (e/fn [] (dom/text email))
     :order/gender (e/fn [] ($ Typeahead gender (e/fn [search] ($ Genders db search))))
     :order/shirt-size (e/fn [] ($ Typeahead shirt-size (e/fn [search] ($ Shirt-sizes db gender search))))}))

#?(:cljs (def !colspec (atom [:db/id :order/email :order/gender :order/shirt-size])))
(comment
  (reset! !colspec [:db/id :order/email])
  (reset! !colspec [:db/id :order/email :order/gender :order/shirt-size]))

(e/defn WebviewGeneric []
  (e/client
    (let [db (e/server (e/watch conn))
          search ($ SearchInput)
          colspec (e/diff-by identity (e/watch !colspec))]
      ($ GenericTable
        colspec
        ($ e/Partial Teeshirt-orders db search)
        ($ e/Partial Record db)))))
