(ns london-talk-2024.webview-generic
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]
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
            (let [search ($ dom/On "input" #(-> % .-target .-value))]
              (binding [dom/node container] ; portal
                (dom/ul
                  (e/cursor [id ($ Options search)]
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

(e/defn GenericTable [Query Row]
  (e/client
    (dom/div
      (let [search (dom/input (dom/props {:placeholder "Filter..."})
                     ($ dom/On "input" #(-> % .-target .-value)))]
        (dom/table (dom/props {:class "hyperfiddle"})
          (e/cursor [x ($ Query search)]
            (dom/tr
              ($ Row x))))))))

(e/defn Render-order [db id]
  (let [!e (e/server (d/entity db id))
        email (e/server (-> !e :order/email))
        gender (e/server (-> !e :order/gender :db/ident))
        shirt-size (e/server (-> !e :order/shirt-size :db/ident))]
    (dom/td (dom/text id))
    (dom/td (dom/text email))
    (dom/td ($ Typeahead gender (e/fn [search] ($ Genders db search))))
    (dom/td ($ Typeahead shirt-size (e/fn [search] ($ Shirt-sizes db gender search))))))

(e/defn WebviewGeneric []
  (let [db (e/server (e/watch conn))]
    ($ GenericTable
      ($ e/Partial Teeshirt-orders db)
      ($ e/Partial Render-order db))))
