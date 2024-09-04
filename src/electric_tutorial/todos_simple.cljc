(ns electric-tutorial.todos-simple
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

#?(:clj (defonce !conn (d/create-conn {}))) ; database on server

#?(:clj (defn todo-count [db]
          (count (d/q '[:find [?e ...] :in $ ?status
                        :where [?e :task/status ?status]]
                   db :active))))

#?(:clj (defn todo-records [db]
          (sort-by :task/description
            (d/q '[:find [(pull ?e [:db/id :task/description]) ...]
                   :where [?e :task/status]] db))))

(e/defn TodoItem [db id]
  (e/client
    (let [!e (e/server (d/entity db id))]
      (dom/li
        (dom/input (dom/props {:type "checkbox", :id id})
          (let [v (dom/On "change" #(-> % .-target .-checked))]
            (if-some [t (e/Token v)]
              (t (e/server ({} (d/transact! !conn [{:db/id id, :task/status (if v :done :active)}]) nil)))
              (when-not (dom/Focused?)
                (set! (.-checked dom/node) (case (e/server (:task/status !e))
                                             :active false, :done true))))))
        (dom/label (dom/props {:for id})
          (dom/text (e/server (:task/description !e))))))))

#?(:cljs (defn read! [node]
           (when-some [v (not-empty (subs (.-value node) 0 100))]
             (set! (.-value node) "") v)))

#?(:cljs (defn enter [e] (when (= "Enter" (.-key e)) (read! (.-target e)))))

(e/defn TodoCreate [!conn]
  (e/client
    (dom/input (dom/props {:placeholder "Buy milk", :maxLength 100})
      (e/for [[v t] (dom/OnAll "keydown" enter)]
        (let [tx [{:task/description v, :task/status :active}]]
          (t (e/server ({} (d/transact! !conn tx) nil))))))))

(e/defn TodoList []
  (e/server
    (let [db (e/watch !conn)]
      (dom/div (dom/props {:class "todo-list"})
        (TodoCreate !conn)
        (e/amb ; hack
          (dom/ul (dom/props {:class "todo-items"})
            (e/for [{:keys [db/id]} (e/diff-by :db/id (e/Offload #(todo-records db)))]
              (TodoItem db id)))
          (dom/p (dom/text (e/Offload #(todo-count db)) " items left")))))))