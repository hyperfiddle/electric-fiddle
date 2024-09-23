(ns electric-tutorial.webview1
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj
   (defonce conn ; state survives reload
     (doto (d/create-conn {:order/email {}})
       (d/transact! ; test data
         [{:order/email "alice@example.com" :order/gender :order/female}
          {:order/email "bob@example.com" :order/gender :order/male}
          {:order/email "charlie@example.com" :order/gender :order/male}]))))

#?(:clj (defn teeshirt-orders [db ?email]
          (sort
            (d/q '[:find [?e ...] ; e.g. [9 10 11]
                   :in $ ?needle :where
                   [?e :order/email ?email]
                   [(clojure.string/includes? ?email ?needle)]]
              db (or ?email "")))))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search)))))

(e/defn Webview1 []
  (dom/props {:class "webview"})
  (e/server
    (let [db (e/watch conn) ; reactive "database value"
          search (dom/input (dom/props {:placeholder "Filter..."})
                   (dom/on "input" #(-> % .-target .-value) ""))
          ids (Teeshirt-orders db search)]
      (e/client (e/Tap-diffs ids))
      (dom/table
        (e/for [id ids] ; e/for is server-sited
          (let [!e (d/entity db id)] ; no round trip!
            (dom/tr
              (dom/td (dom/text id))
              (dom/td (dom/text (:order/email !e)))
              (dom/td (dom/text (:order/gender !e))))))))))