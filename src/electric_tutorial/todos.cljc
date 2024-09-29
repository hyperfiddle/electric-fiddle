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
  (e/for [[t v] (InputSubmitCreate! :placeholder "Buy milk")]
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

;; #?(:clj (defn cmd->tx [xcmd]
;;           (match xcmd
;;             [::create-todo desc tempid] [{:task/description desc, :task/status :active}]
;;             [::toggle e status] [{:db/id e, :task/status status}]
;;             [::edit-todo-desc id desc] [{:db/id id :task/description desc}]
;;             :else nil)))

(defn expand-tx-effects [!conn form]
  (->> (group-by first form) ; TodoMVC does not have any batch forms, no need to group - todo
    (mapcat (fn [[cmd xargs]]
              (letfn [(create-todo [[_ desc tempid]] [{:task/description desc, :task/status :active}])
                      (toggle [[_ e status]] [{:db/id e, :task/status status}])
                      (edit-todo-desc [[_ id desc]] [{:db/id id :task/description desc}])]
                (let [f (get {::create-todo create-todo
                              ::toggle toggle
                              ::edit-todo-desc edit-todo-desc} cmd)
                      tx (into [] (mapcat f) xargs)]
                  [[#?(:clj (partial d/transact! !conn) :cljs nil) tx]]))))))

#?(:clj (def !conn (doto (d/create-conn {})
                     (d/transact!
                       [{:task/description "feed baby" :task/status :active}
                        {:task/description "buy milk" :task/status :active}
                        {:task/description "call mom" :task/status :active}]))))

(e/defn Todos []
  (let [db (e/server (e/watch !conn))]
    (Service (e/server (identity (partial expand-tx-effects !conn)))
      (e/with-cycle* first [edits (e/amb)]
        (e/Filter some?
          (TodoList db edits))))))