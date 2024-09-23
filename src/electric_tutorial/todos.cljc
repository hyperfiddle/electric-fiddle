(ns electric-tutorial.todos
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [PendingController]]
            [hyperfiddle.input-zoo0 :refer
             [InputSubmit! InputSubmitCreate! CheckboxSubmit!]]))

(e/defn Todo-count [db edits]
  (e/server
    (e/Offload
      #(let [xs (d/q '[:find [?e ...] :in $ ?status
                       :where [?e :task/status ?status]]
                  db :active)] (count xs)))))

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
  (e/for [[t v] (InputSubmitCreate! :placeholder "Buy milk")] ; dom/onall bad
    (let [tempid t]
      [t [::create-todo v tempid]
       {tempid {:db/id tempid :task/description v :task/status :active}}])))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li #_(dom/props {:style {:background-color (when pending "yellow")}}) ; pending at collection level
    (e/amb
      (e/for [[t v] (CheckboxSubmit! (case status :active false, :done true) #_#_:label description :id id)] ; pending at value level
        [t [::toggle id v] {id (-> m (dissoc ::pending) (assoc :task/status v))}])
      (e/for [[t v] (InputSubmit! description :token pending)]
        (prn 'InputSubmit v)
        [t [::edit-todo-desc id v] {id (assoc m :task/description v)}]))))

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

#?(:clj (defn cmd->tx [xcmd]
          (match xcmd
            [::create-todo desc tempid] [{:task/description desc, :task/status :active}]
            [::toggle e status] [{:db/id e, :task/status (if status :done :active)}]
            [::edit-todo-desc id desc] [{:db/id id :task/description desc}]
            :else nil)))

#?(:clj (def !conn (doto (d/create-conn {}) ; database on server
                     (d/transact! ; test data
                       [{:task/description "feed baby" :task/status :active}
                        {:task/description "buy milk" :task/status :active}
                        {:task/description "call mom" :task/status :active}]))))

(e/defn Todos []
  (e/client ; bias for writes because token doesn't transfer
    (let [db (e/server (e/watch !conn))
          [ts xcmds :as edits] (e/with-cycle* first [edits (e/amb)]
                                 (prn 'edits (e/as-vec (second edits)))
                                 (e/Filter some?
                                   (TodoList db edits)))]
      (prn 'service (e/as-vec (second xcmds)))
      (e/for [[t xcmd _ :as edit] edits]
        (let [res (e/server (prn 'xcmd xcmd)
                    (let [tx (cmd->tx xcmd)] ; secure
                      (e/Offload #(try (prn 'tx tx) (Thread/sleep 1000)
                                    (assert false "die") ; random failure
                                    (d/transact! !conn tx) (doto [::ok] (prn 'tx-success))
                                    (catch Exception e [::fail (str e)])))))
              [status err] res]
          (cond
            (= status ::ok) (t)
            (= status ::fail) (t err)))))))