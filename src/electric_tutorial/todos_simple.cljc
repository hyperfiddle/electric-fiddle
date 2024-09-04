(ns electric-tutorial.todos-simple
  (:require [contrib.data :refer [index-by]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.incseq :as i]
            [missionary.core :as m]))

#?(:clj (def !conn (d/create-conn {}))) ; database on server

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
        (dom/label (dom/props {:for id}) (dom/text label) (e/amb)))))) ; todo bundle e/amb in all elements

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
      (let [id (random-uuid)]
        [t
         id
         [::create-todo v]
         {:task/description v :task/status :active}]))))

(e/defn TodoItem [{:keys [db/id task/status task/description ::pending] :as m}]
  (e/client
    (dom/props {:style {:background-color (when pending "yellow")}})
    (e/for [[v t] (Checkbox (case status :active false, :done true) description id)]
      [t
       id ; stable id
       [::toggle id v] ; xcmd
       (-> m (dissoc ::pending) (assoc :task/status v)) ; prediction
       ])))

#?(:cljs (def !pending-records (atom {}))) ; id -> [tx, prediction]

(defn cmd->tx [[cmd a b & args]]
  (case cmd
    ::create-todo [{:task/description a, :task/status :active}]
    ::toggle [{:db/id a, :task/status (if b :done :active)}]
    nil))

(e/defn Reconcile-records [as bs]
  (e/client
    (->> (merge
           (index-by :db/id as)
           (index-by :db/id bs))
      vals
      (sort-by :task/description)
      (e/diff-by :db/id))))

#?(:clj (defn slow-transact! [& args] (Thread/sleep 500) (apply d/transact! args)))

(e/defn TodoList []
  (e/client ; bias for writes because token doesn't transfer
    (let [db (e/server (e/watch !conn))
          todos (Reconcile-records
                  (e/as-vec (Todo-records db))
                  (vals (e/watch !pending-records)))]

      (prn 'pending (e/watch !pending-records))
      (prn 'todos (e/as-vec todos))

      (e/for [[t id xcmd prediction]
              (dom/div (dom/props {:class "todo-list"})
                (e/amb ; hack
                  (TodoCreate)
                  (dom/ul (dom/props {:class "todo-items"})
                    (e/for [m todos]
                      (dom/li (TodoItem m))))
                  (dom/p (dom/text (Todo-count db) " items left") (e/amb))))]

        (prn 'top xcmd)
        (prn 'prediction id prediction)
        (swap! !pending-records assoc id (assoc prediction ::pending true))

        (case (e/server
                (when-some [tx (doto (cmd->tx xcmd) prn)] ; secure
                  (case (e/Offload #(slow-transact! !conn tx)) ::ok)))
          ::ok ({} (swap! !pending-records dissoc id) (t)))))))