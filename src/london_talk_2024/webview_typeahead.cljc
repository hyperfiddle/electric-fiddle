(ns london-talk-2024.webview-typeahead
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin
                     :refer [conn teeshirt-orders genders shirt-sizes]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [missionary.core :as m]))

(e/defn Typeahead [v-id Options OptionLabel]
  (e/client
    (dom/div (dom/props {:class "hyperfiddle-typeahead"})
      (let [container dom/node
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

(e/defn Tap-diffs [tap! x]
  (tap! (e/input (e/pure x)))
  x)

(e/defn Genders [db search]
  (e/server (e/diff-by identity ($ e/Offload #(genders db search)))))

(e/defn Shirt-sizes [db gender search]
  (e/server (e/diff-by identity ($ e/Offload #(shirt-sizes db gender search)))))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity ($ e/Offload #(teeshirt-orders db search)))))

(e/defn Teeshirt-orders-view [db]
  (dom/div
    (let [search (dom/input (dom/props {:placeholder "Filter..."})
                   ($ dom/On "input" #(-> % .-target .-value)))]

      (dom/table (dom/props {:class "hyperfiddle"})
        (e/cursor [id ($ Teeshirt-orders db search)]
          (let [m (e/server
                    (d/pull db [:order/email
                                {:order/gender [:db/ident]}
                                {:order/shirt-size [:db/ident]}] id))
                gender (e/server (:db/ident (:order/gender m)))]
            (dom/tr
              (dom/td (dom/text id))
              (dom/td (dom/text (e/server (:order/email m))))
              (->> (dom/td ($ Typeahead gender
                             (e/fn Options [search] ($ Genders db search))
                             (e/fn OptionLabel [x] x)))
                (println 'gender))
              (->> (dom/td ($ Typeahead (e/server (:db/ident (:order/shirt-size m)))
                             (e/fn Options [search] ($ Shirt-sizes db gender search))
                             (e/fn OptionLabel [x] x)))
                (println 'shirt-size)))))))))

(e/defn WebviewTypeahead []
  (e/client
    ($ Teeshirt-orders-view (e/server (e/watch conn)))))