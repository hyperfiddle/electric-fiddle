(ns london-talk-2024.webview
  (:require #?(:clj [models.teeshirt-orders-datascript-dustin :refer [conn teeshirt-orders]])
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric-de :as e :refer [$]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.rcf :refer [tests]]))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity ($ e/Offload #(teeshirt-orders db search)))))

(tests (teeshirt-orders @conn "") := [9 10 11])

(e/defn Teeshirt-orders-view [db]
  (dom/div
    (let [search (dom/input (dom/props {:placeholder "Filter..."})
                   ($ dom/On "input" #(-> % .-target .-value)))]
      (dom/table (dom/props {:class "hyperfiddle"})
        (e/cursor [id ($ Teeshirt-orders db search)]
          #_(println id)
          (let [!e (e/server (d/entity db id))]
            (dom/tr
              (dom/td (dom/text id))
              (dom/td (dom/text (e/server (:order/email !e))))
              (dom/td (dom/text (e/server (:db/ident (:order/gender !e)))))
              (dom/td (dom/text (e/server (:db/ident (:order/shirt-size !e))))))))))))

(e/defn Webview []
  (e/client
    ($ Teeshirt-orders-view (e/server (e/watch conn)))))
