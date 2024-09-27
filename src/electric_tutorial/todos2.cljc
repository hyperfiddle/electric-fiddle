(ns electric-tutorial.todos2
  (:require #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service PendingController]]
            [hyperfiddle.input-zoo0 :refer
             [Input! Checkbox! InputSubmitCreate!]]
            [electric-tutorial.todos :refer [#?(:clj !conn) expand-tx-effects]]))

(e/defn Todo-records [db edits]
  (e/client
    (prn 'TodoRecords 'edits (e/as-vec (second edits)))
    (PendingController :db/id :task/description edits
      (e/server
        (e/diff-by :db/id
          (e/Offload ; good reason to not require offload to have # ?
            #(try
               (->> (d/q '[:find [(pull ?e [:db/id
                                            :task/status
                                            :task/description]) ...]
                           :where [?e :task/status]] db)
                 (sort-by :task/description))
               (catch Exception e (prn e)))))))))

(e/defn TodoCreate []
  #_(Form :show-buttons false) ; todo?
  (e/for [[t v] (InputSubmitCreate! :placeholder "Buy milk")] ; dom/onall bad
    [t [[::create-todo v t]]
     {t {:db/id t :task/description v :task/status :active}}]))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li #_(dom/props {:style {:background-color (when pending "yellow")}}) ; pending at collection level
    (e/amb
      (Form
        (e/for [[t v] (Checkbox! (= :done status))]
          [t [[::toggle id (if v :done :active)]]
           {id (-> m (dissoc ::pending) (assoc :task/status v))}])
        :show-buttons false :auto-submit true)
      (Form
        (e/for [[t v] (Input! description :token pending)]
          [t [[::edit-todo-desc id v]] {id (assoc m :task/description v)}])
        :show-buttons false))))

(e/defn TodoList [db edits]
  (dom/div (dom/props {:class "todo-list"})
    (let [todos (Todo-records db edits)]
      (prn 'todos (e/as-vec todos))
      (e/amb
        (TodoCreate)
        (dom/ul (dom/props {:class "todo-items"})
          (e/for [m todos]
            (TodoItem m)))
        (dom/p (dom/text (e/Count todos) " items left"))))))

(e/defn Todos2 []
  (let [db (e/server (e/watch !conn))]
    (Service (e/server (identity expand-tx-effects))
      (e/with-cycle* first [edits (e/amb)]
        (e/Filter some?
          (TodoList db edits))))))