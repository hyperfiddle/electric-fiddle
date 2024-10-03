(ns electric-tutorial.todos
  (:require #_[clojure.core.match :refer [match]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form! Service PendingController]]
            [hyperfiddle.input-zoo0 :refer
             [Input! Checkbox! Button!]]))

(e/defn Todo-records [db forms] ; todo field awareness
  (e/client
    (prn 'Todo-records 'forms (e/as-vec (second forms)))
    (PendingController :db/id :task/description forms ; rebind transact to with, db must escape
      #_(e/fn [db])
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
  (Form! (Input! ::create "" :placeholder "Buy milk") ; press enter
    :genesis true ; immediately consume form, ready for next submit
    :commit (fn [{v ::create :as dirty-form} tempid]
              (prn 'TodoCreate-commit dirty-form)
              [[`Create-todo tempid v] {tempid {:db/id tempid :task/description v :task/status :active}}])
    :show-buttons true :debug true))

(e/defn TodoItem [{:keys [db/id task/status task/description ::cqrs/pending] :as m}]
  (dom/li
    (Form!
      (e/amb
        (Form! (Checkbox! :task/status (= :done status)) ; FIXME clicking commit button on top of autocommit generates broken doubled tx
          :name ::toggle
          :commit (fn [{v :task/status}] [[`Toggle id (if v :done :active)]
                                          {id (-> m (dissoc ::pending) (assoc :task/status v))}])
          :show-buttons false :auto-submit true)
        (Form! (Input! :task/description description)
          :name ::edit-desc
          :commit (fn [{v :task/description}] [[`Edit-todo-desc id v]
                                               {id (assoc m :task/description v)}])
          :show-buttons false)
        (Form! (Button! {} :label "X" :class "destroy" :disabled (some? pending))
          :auto-submit true :show-buttons false
          :name ::destroy
          :commit (fn [_] [[`Delete-todo id] {id ::cqrs/retract}]))

        (if-let [[t xcmd guess] pending]
          [t {::pending xcmd} guess]
          (e/amb)))
      :auto-submit false :show-buttons true :debug true
      :commit (fn [{:keys [::toggle ::edit-desc ::destroy ::pending]}]
                (doto [[`Batch toggle edit-desc destroy pending] {}] (prn 'Form-outer))
                #_[(doto destroy (prn 'commit-batch)) {}]
                #_(let [[_ id status] toggle
                        [_ id v] create
                        [_ id v'] edit-desc
                        [_ id] destroy]
                    (cond
                      (and create destroy) nil #_[nil dirty-form-guess]
                      (and create edit) [[`Create-todo (or v' v)] dirty-form-guess]))))))

(e/defn TodoList [db forms]
  (dom/div (dom/props {:class "todo-list"})
    (let [forms' (TodoCreate) ; transfer responsibility to pending item form
          todos (Todo-records db (e/amb forms forms'))]
      (prn 'todos (e/as-vec todos))
      (e/amb
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

(e/defn Delete-todo [id] ; FIXME retractEntity works but todo item stays on screen, who is retaining it?
  (e/server
    (let [tx [[:db/retractEntity id]]]
      (e/Offload #(try (slow-transact! !conn tx) (doto ::cqrs/ok (prn `Delete))
                       (catch Exception e (doto ::fail (prn e))))))))

(e/defn Batch [& forms]
  (prn 'Batch forms)
  #_(doseq [form forms] (Service form)))

(e/defn Todos []
  (e/client
    (binding [cqrs/*effects* {`Create-todo Create-todo
                              `Edit-todo-desc Edit-todo-desc
                              `Toggle Toggle
                              `Delete-todo Delete-todo
                              `Batch Batch}]
      (let [db (e/server (e/watch !conn))]
        (Service
          (e/with-cycle* first [forms (e/amb)]
            (e/Filter some?
              (TodoList db forms))))))))