(ns electric-tutorial.todos
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.chat :refer [InputSubmit]]))

(e/defn Todo-count [db]
  (e/server
    (e/Offload
      #(let [xs (d/q '[:find [?e ...] :in $ ?status
                       :where [?e :task/status ?status]]
                  db :active)] (count xs)))))

(e/defn Todo-records [db]
  (e/server
    (->> (e/Offload ; good reason to not require offload to have # ?
           #(->> (d/q '[:find [(pull ?e [:db/id
                                         :task/status
                                         :task/description]) ...]
                        :where [?e :task/status]] db)
              (sort-by :task/description)))
      (e/diff-by :db/id))))

(e/defn Checkbox [checked label id]
  (e/client
    (let [id (or id (random-uuid))]
      (e/amb
        (dom/input (dom/props {:type "checkbox", :id id})
          (let [pending (dom/OnAll "change" #(-> % .-target .-checked))]
            (when-not (or (dom/Focused?) ; why? it's not an input
                        (pos? (e/Count pending))) ; do not accept controlled values until caught up
              (set! (.-checked dom/node) checked))
            pending))
        (dom/label (dom/props {:for id}) (dom/text label)))))) ; todo bundle e/amb in all elements

(e/defn TodoCreate []
  (e/client
    (e/for [[v t] (InputSubmit :placeholder "Buy milk")]
      (let [id (random-uuid)]
        [t [::create-todo v id]]))))

(e/defn TodoItem [{:keys [db/id task/status task/description]}]
  (e/client
    (dom/li
      (e/for [[v t] (Checkbox (case status :active false, :done true) description id)]
        [t [::toggle id v]]))))

(e/defn TodoList [db]
  (dom/div (dom/props {:class "todo-list"})
    (e/amb
      (TodoCreate)
      (dom/ul (dom/props {:class "todo-items"})
        (e/for [m (Todo-records db)]
          (TodoItem m)))
      (dom/p (dom/text (Todo-count db) " items left")))))

#?(:clj (defn cmd->tx [[cmd a b & args]]
          (case cmd
            ::create-todo [{:task/description a, :task/status :active}]
            ::toggle [{:db/id a, :task/status (if b :done :active)}]
            nil)))

#?(:clj (defonce !conn (doto (d/create-conn {}) ; database on server
                         (d/transact! ; test data
                           [{:task/description "feed baby" :task/status :active}
                            {:task/description "buy milk" :task/status :active}
                            {:task/description "call mom" :task/status :active}]))))

(e/defn Todos []
  (e/client ; bias for writes because token doesn't transfer
    (let [db (e/server (e/watch !conn))]
      (e/for [[t xcmd] (TodoList db)]
        (case (e/server
                (when-some [tx (cmd->tx xcmd)] ; secure
                  (case (e/Offload #(d/transact! !conn tx)) ::ok)))
          ::ok (t))))))