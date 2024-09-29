(ns electric-tutorial.todos
  (:require #_[clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service PendingController]]
            [hyperfiddle.input-zoo0 :refer
             [Input! Checkbox! InputSubmitCreate!]]))

(e/defn Todo-records [db edits] ; todo field awareness
  (e/client
    (prn 'Todo-records 'edits (e/as-vec (second edits)))
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
  (e/for [[t v] (InputSubmitCreate! :placeholder "Buy milk") #_(Input! "")]
    [t [`Create-todo t v] {t {:db/id t :task/description v :task/status :active}}]))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li #_(dom/props {:style {:background-color (when pending "yellow")}}) ; pending at collection level
    (e/amb
      (Form (Checkbox! (= :done status) :parse #(hash-map 0 %))
        :commit (fn [dirties]
                  (let [{v 0} (apply merge dirties)]
                    [[`Toggle id (if v :done :active)]
                     {id (-> m (dissoc ::pending) (assoc :task/status v))}]))
        :show-buttons false :auto-submit true)
      (Form (Input! description :token pending :parse #(hash-map 0 %))
        :commit (fn [dirties]
                  (let [{v 0} (apply merge dirties)]
                    [[`Edit-todo-desc id v] {id (assoc m :task/description v)}]))
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

#?(:clj (def !conn (doto (d/create-conn {})
                     (d/transact!
                       [{:task/description "feed baby" :task/status :active}
                        {:task/description "buy milk" :task/status :active}
                        {:task/description "call mom" :task/status :active}]))))

#?(:clj (defn slow-transact! [!conn tx] (Thread/sleep 1000) (d/transact! !conn tx)))

(e/defn Create-todo [tempid desc]
  (e/server
    (let [tx [{:task/description desc, :task/status :active}]]
      (e/Offload #(try (slow-transact! !conn tx) (doto ::cqrs/ok (prn `Create-todo))
                    (catch Exception e (doto ::fail (prn e))))))))

(e/defn Edit-todo-desc [id desc]
  (e/server
    (let [tx [{:db/id id :task/description desc}]]
      (prn 'EditTodoDesc tx)
      (e/Offload #(try (slow-transact! !conn tx) (doto ::cqrs/ok (prn `Edit-todo-desc))
                    (catch Exception e (doto ::fail (prn e))))))))

(e/defn Toggle [id status]
  ;; (prn 'Toggle id status) (e/server (prn 'Toggle id status))
  (e/server
    (let [tx [{:db/id id, :task/status status}]]
      (e/Offload #(try (slow-transact! !conn tx) (doto ::cqrs/ok (prn `Toggle))
                    (catch Exception e (doto ::fail (prn e))))))))

(e/defn Todos []
  (e/client
    (binding [cqrs/*effects* {`Create-todo Create-todo
                              `Edit-todo-desc Edit-todo-desc
                              `Toggle Toggle}]
      (let [db (e/server (e/watch !conn))]
        (Service
          (e/with-cycle* first [edits (e/amb)]
            (e/Filter some?
              (TodoList db edits))))))))