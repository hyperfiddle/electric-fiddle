(ns electric-tutorial.webview1
  (:require #?(:clj [datascript.core :as d]) ; server database
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
          (sort-by :order/email
            (d/q '[:find [(pull ?e [:db/id :order/email :order/gender]) ...]
                   :in $ ?needle :where
                   [?e :order/email ?email]
                   [(clojure.string/includes? ?email ?needle)]]
              db (or ?email "")))))

(e/defn Teeshirt-orders [db search]
  (e/server (e/diff-by identity (e/Offload #(teeshirt-orders db search)))))

(declare css)
(e/defn Webview1 []
  (e/client
    (dom/style (dom/text css))
    (let [db (e/server (e/watch conn))  ; reactive "database value"
          search (dom/input (dom/props {:placeholder "Filter..."})
                   (dom/On "input" #(-> % .-target .-value) ""))
          ids (Teeshirt-orders db search)] ; IO encapsulation - e/server not visible
      (dom/table
        (e/for [{:keys [db/id order/email order/gender]} ids]
          (dom/tr
            (dom/td (dom/text id))
            (dom/td (dom/text email))
            (dom/td (dom/text gender))))))))

(def css "
.user-examples-target table { display: grid; column-gap: 1ch; }
.user-examples-target tr { display: contents; }
.user-examples-target table {grid-template-columns: repeat(3, max-content);}")