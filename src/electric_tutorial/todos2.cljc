(ns electric-tutorial.todos2
  (:require [contrib.data :refer [index-by]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.input-zoo :refer [InputSubmitClear! CheckboxSubmit!]]
            [electric-tutorial.todos :refer [!conn]]))

(e/defn Reconcile-records [stable-kf as bs]
  (e/client
    (->> (merge
           (index-by stable-kf as)
           (index-by stable-kf bs))
      vals
      (sort-by :task/description)
      (e/diff-by stable-kf))))

(e/defn CrudList [Query List-wrap Create Edit]
  (e/client
    (let [!pending (atom {}) ; id -> [tx, prediction]
          xs (Reconcile-records :db/id ; todo stable-kf
               (e/as-vec (Query)) ; todo differential reconciliation
               (vals (e/watch !pending)))
          edits (e/amb
                  (Create)
                  (List-wrap (e/fn []
                               (e/for [m xs]
                                 (Edit m)))))]
      (e/for [[t id xcmd prediction :as all] edits]
        (swap! !pending assoc id (assoc prediction ::pending true))
        (e/on-unmount #(swap! !pending dissoc id))
        all))))

(e/defn TodoCreate []
  (e/client
    (e/for [[v t] (InputSubmitClear! :placeholder "Buy milk")]
      (let [id (random-uuid)]
        [t
         id
         [::create-todo v]
         {:task/description v :task/status :active}]))))

(e/defn TodoItem [{:keys [db/id task/status task/description ::pending] :as m}]
  (e/client
    (dom/li
      (dom/props {:style {:background-color (when pending "yellow")}})
      (e/for [[v t] (CheckboxSubmit! (case status :active false, :done true)
                      :label description :id id)]
        [t
         id ; stable id
         [::toggle id v] ; xcmd
         (-> m (dissoc ::pending) (assoc :task/status v)) ; prediction
         ]))))

(e/defn Todo-count [db]
  (e/server (e/Offload #(count (d/q '[:find [?e ...] :in $ ?status
                                      :where [?e :task/status ?status]]
                                 db :active)))))

(e/defn Todo-records [db]
  (e/server
    (->> (e/Offload ; good reason to not require offload to have # ?
           #(->> (d/q '[:find [(pull ?e [:db/id
                                         :task/status
                                         :task/description]) ...]
                        :where [?e :task/status]] db)
              (sort-by :task/description)))
      (e/diff-by :db/id))))

(e/defn TodoList* [db]
  (dom/div (dom/props {:class "todo-list"})
    (e/amb
      (CrudList
        (e/Partial Todo-records db)
        (e/fn List-wrap [Body] (dom/ul (dom/props {:class "todo-items"}) (Body)))
        TodoCreate
        TodoItem)
      (dom/p (dom/text (Todo-count db) " items left") (e/amb)))))

(defn cmd->tx [[cmd a b & args]]
  (case cmd
    ::create-todo [{:task/description a, :task/status :active}]
    ::toggle [{:db/id a, :task/status (if b :done :active)}]
    nil))

#?(:clj (defn slow-transact! [& args] (Thread/sleep 500) (apply d/transact! args)))

(e/defn Controller [edits]
  (e/client ; bias for writes because token doesn't transfer
    (e/for [[t id xcmd prediction] edits]
      (case (e/server
              (when-some [tx (cmd->tx xcmd)] ; secure
                (case (e/Offload #(slow-transact! !conn tx)) ::ok)))
        ::ok (t)))))

(e/defn TodoList []
  (let [db (e/server (e/watch !conn))]
    (Controller (TodoList* db))))