(ns electric-tutorial.todos
  (:require [clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [dustingetz.cqrs0 :as cqrs :refer [PendingController]]
            [hyperfiddle.input-zoo0 :refer
             [InputSubmit! InputSubmitClear! CheckboxSubmit!]]))

(e/defn Todo-count [db edits]
  (e/server
    (e/Offload
      #(let [xs (d/q '[:find [?e ...] :in $ ?status
                       :where [?e :task/status ?status]]
                  db :active)] (count xs)))))

(e/defn Todo-records [db edits]
  (e/client
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
  (e/for [[t v] (InputSubmitClear! :placeholder "Buy milk")] ; dom/onall bad
    (let [tempid (random-uuid) #_(hash t)]
      [t [::create-todo v tempid]
       {tempid {:db/id tempid :task/description v :task/status :active}}])))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li (dom/props {:style {:background-color (when pending "yellow")}}) ; pending at collection level
    (e/amb
      (e/for [[t v] (CheckboxSubmit! (case status :active false, :done true) #_#_:label description :id id)] ; pending at value level
        [t [::toggle id v] {id (-> m (dissoc ::pending) (assoc :task/status v))}])
      (e/for [[t v] (InputSubmit! description)]
        [t [::edit-todo-desc id v] {id (assoc m :task/description v)}]))))

(e/defn TodoList [db edits]
  (dom/div (dom/props {:class "todo-list"})
    (e/amb
      (TodoCreate)
      (dom/ul (dom/props {:class "todo-items"})
        (e/for [m (Todo-records db edits)]
          (TodoItem m)))
      (dom/p (dom/text (Todo-count db edits) " items left")))))

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
          edits (e/with-cycle* first [edits (e/amb)]
                  (e/Filter some?
                    (TodoList db edits)))]
      (e/for [[t xcmd _ :as edit] edits]
        (prn xcmd)
        #_(case (e/server
                  (when-some [tx (cmd->tx xcmd)] ; secure
                    (case (e/Offload #(d/transact! !conn tx)) ::ok)))
            ::ok (t))))))