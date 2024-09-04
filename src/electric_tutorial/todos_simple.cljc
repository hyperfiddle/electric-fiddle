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

(e/defn Checkbox [checked label id]
  (e/client
    (e/amb
      (dom/input (dom/props {:type "checkbox", :id id})
        (let [pending (dom/OnAll "change" #(-> % .-target .-checked))]
          (when-not (or (dom/Focused?) ; why? it's not an input
                      (pos? (e/Count pending))) ; do not accept controlled values until caught up
            (set! (.-checked dom/node) checked))
          pending))
      (dom/label (dom/props {:for id}) (dom/text label) (e/amb))))) ; todo bundle e/amb in all elements

#?(:cljs (defn read! [node]
           (when-some [v (not-empty (subs (.-value node) 0 100))]
             (set! (.-value node) "") v)))

#?(:cljs (defn enter [e] (when (= "Enter" (.-key e)) (read! (.-target e)))))

(e/defn InputSubmit [& {:as props}]
  (e/client
    (dom/input (dom/props props) (dom/props {:maxLength 100})
      (dom/OnAll "keydown" enter))))

(e/defn TodoCreate []
  (e/client
    (e/for [[v t] (InputSubmit :placeholder "Buy milk")]
      [t ::create-todo v])))

(e/defn TodoItem [db id]
  (e/client
    (let [!e (e/server (d/entity db id))]
      (e/for [[v t] (Checkbox
                      (case (e/server (:task/status !e)) :active false, :done true)
                      (e/server (:task/description !e)) id)]
        [t ::toggle id v]))))

(e/defn TodoList []
  (e/client ; bias for writes
    (let [db (e/server (e/watch !conn))
          todos (e/server (e/diff-by :db/id (e/Offload #(todo-records db))))]
      (e/for [[t cmd a b & args]
              (dom/div (dom/props {:class "todo-list"})
                (e/amb ; hack
                  (TodoCreate)
                  (dom/ul (dom/props {:class "todo-items"})
                    (e/for [{:keys [db/id]} todos]
                      (dom/li (TodoItem db id))))
                  (dom/p (dom/text (e/server (e/Offload #(todo-count db))) " items left"))))]

        (case (e/server ; security
                (when-some [tx (case cmd
                                 ::create-todo [{:task/description a, :task/status :active}]
                                 ::toggle [{:db/id a, :task/status (if b :done :active)}]
                                 nil)]
                  ({} (d/transact! !conn tx) ::ok)))
          ::ok (t))))))