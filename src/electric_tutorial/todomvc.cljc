(ns electric-tutorial.todomvc
  (:require [contrib.data :refer [unqualify]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.forms6-inline-submit-builtin :refer [Form]]
            [hyperfiddle.input-zoo0 :as z :refer
             [Input! Checkbox! Button!
              InputSubmitCreate?!]]))

(e/defn PendingMonitor [edits]
  (when (pos? (e/Count edits)) (dom/props {:aria-busy true}))
  edits)

#?(:clj
   (defn query-todos [db filter]
     {:pre [filter]}
     (case filter
       :active (d/q '[:find [?e ...] :where [?e :task/status :active]] db)
       :done   (d/q '[:find [?e ...] :where [?e :task/status :done]] db)
       :all    (d/q '[:find [?e ...] :where [?e :task/status]] db))))

#?(:clj
   (defn todo-count [db filter]
     {:pre  [filter]
      :post [(number? %)]}
     (-> (case filter
           :active (d/q '[:find (count ?e) . :where [?e :task/status :active]] db)
           :done   (d/q '[:find (count ?e) . :where [?e :task/status :done]] db)
           :all    (d/q '[:find (count ?e) . :where [?e :task/status]] db))
       (or 0)))) ; datascript can return nil wtf

(e/defn Filter-control [state target label]
  (PendingMonitor
    (dom/a (dom/props {:class (when (= state target) "selected")})
      (dom/text label)
      (e/for [[t _] (dom/On-all "click" (constantly true))]
        [t [`Set-filter target]]))))

(e/defn TodoStats [db state]
  (let [active (e/server (todo-count db :active))
        done   (e/server (todo-count db :done))]
    (dom/div
      (e/amb
        (dom/span (dom/props {:class "todo-count"})
          (dom/strong (dom/text active))
          (dom/span (dom/text " " (str (case active 1 "item" "items")) " left")))

        (dom/ul (dom/props {:class "filters"})
          (e/amb
            (dom/li (Filter-control (::filter state) :all "All"))
            (dom/li (Filter-control (::filter state) :active "Active"))
            (dom/li (Filter-control (::filter state) :done "Completed"))))

        (when (pos? done)
          (dom/button (dom/props {:class "clear-completed"})
            (dom/text "Clear completed " done)
            (e/for [[t _] (dom/On-all "click" (constantly true))]
              [t [`Clear-completed]])))))))

(e/defn TodoItem [db state id]
  (let [!e (e/server (d/entity db id))
        status (e/server (:task/status !e))
        description (e/server (:task/description !e))]
    (dom/li
      (e/amb
        (dom/props {:class [(when (= :done status) "completed")
                            (when (= id (::editing state)) "editing")]})
        (dom/div (dom/props {:class "view"})
          (e/amb
            (e/for [[t v] (Checkbox! (= :done status) :class "toggle" :id id)]
              (let [status (case v true :done, false :active, nil)]
                [t [`Toggle id status]]))
            (dom/label (dom/text description)
              (e/for [[t _] (dom/On-all "dblclick" (constantly true))]
                [t [`Editing-item id]]))))
        (when (= id (::editing state))
          (dom/span (dom/props {:class "input-load-mask"})
            (doto (Form (e/for [[t v] (Input! description :class "edit" :autofocus true)] [t [v]]) ; simulate Field
                    ::z/discard `[Cancel-todo-edit-desc]
                    ::z/commit (fn [[%]] [`Edit-todo-desc id %]))
              (prn 'form))))
        (dom/button (dom/props {:class "destroy"}) ; todo Button!
          (PendingMonitor
            (e/for [[t _] (dom/On-all "click" (constantly true))]
              [t [`Delete-todo id]])))))))

(e/defn Query-todos [db filter]
  (e/server (e/diff-by identity (sort (query-todos db filter)))))

(e/defn TodoList [db state]
  (dom/div
    (dom/section (dom/props {:class "main"})
      (e/amb
        (let [active (e/server (todo-count db :active))
              all    (e/server (todo-count db :all))
              done   (e/server (todo-count db :done))]
          (e/for [[t v] (Checkbox! (cond (= all done) true (= all active) false :else nil)
                          :class "toggle-all")]
            (let [status (case v (true nil) :done, false :active)]
              [t [`Toggle-all status]])))
        (dom/label (dom/props {:for "toggle-all"}) (dom/text "Mark all as complete"))
        (dom/ul (dom/props {:class "todo-list"})
          (e/for [id (Query-todos db (::filter state))]
            (TodoItem db state id)))))))

(e/defn CreateTodo []
  (dom/span (dom/props {:class "input-load-mask"})
    (e/for [[t v] (InputSubmitCreate?! :class "new-todo input-load-mask"
                    :placeholder "What needs to be done?")]
      [t [`Create-todo v]])))

(e/defn TodoMVC-UI [db state]
  (dom/section (dom/props {:class "todoapp"})
    (e/amb
      (dom/header (dom/props {:class "header"})
        (CreateTodo))
      (e/When (e/server (pos? (todo-count db :all)))
        (TodoList db state))
      (dom/footer (dom/props {:class "footer"})
        (TodoStats db state)))))

(e/defn Diagnostics [db state]
  (dom/dl
    (dom/dt (dom/text "count :all")) (dom/dd (dom/text (pr-str (e/server (todo-count db :all)))))
    (dom/dt (dom/text "query :all")) (dom/dd (dom/text (pr-str (e/server (query-todos db :all)))))
    (dom/dt (dom/text "state")) (dom/dd (dom/text (pr-str (update-keys state unqualify))))
    (dom/dt (dom/text "delay")) (dom/dd (e/amb (e/for [[t v] (Input! (::delay state)
                                                               :type "number" :step 1 :min 0
                                                               :style {:width :min-content})]
                                                 [t [`Set-delay (parse-long v)]])
                                          (dom/text " ms")))))

(e/defn Slow-transact [delay !conn tx]
  (e/server (e/Offload #(try (Thread/sleep delay) (d/transact! !conn tx) ::ok
                          (catch InterruptedException _) ; never seen
                          (catch Exception _ ::fail)))))

(def Transact! nil)
(def db nil)
(def !state nil)
(def state nil)

(e/defn Clear-completed []
  (e/server (->> (seq (query-todos db :done))
              (mapv (fn [id] [:db/retractEntity id])) Transact!)))

(e/defn Toggle [id status]
  (e/client (case (e/server (Transact! [{:db/id id, :task/status status}]))
              (swap! !state assoc ::editing nil))))

(e/defn Toggle-all [status]
  (e/server (->> (query-todos db (if (= :done status) :active :done))
              (mapv (fn [id] {:db/id id, :task/status status})) Transact!)))

(e/defn Cancel-todo-edit-desc [] (e/client (swap! !state assoc ::editing nil)))
(e/defn Delete-todo [id] (e/server (Transact! [[:db/retractEntity id]])))
(e/defn Create-todo [desc] (e/server (Transact! [{:task/description desc, :task/status :active}])))
(e/defn Editing-item [id] (e/client (swap! !state assoc ::editing id)))
(e/defn Edit-todo-desc [id desc]
  (e/client (case (e/server (Transact! [{:db/id id, :task/description desc}]))
              (swap! !state assoc ::editing nil))))

(e/defn Set-delay [v] (e/client (swap! !state assoc ::delay v)))
(e/defn Set-filter [target] (e/client (swap! !state assoc ::filter target)))

(e/defn Effects []
  (e/client
    {`Clear-completed Clear-completed
     `Toggle Toggle
     `Editing-item Editing-item
     `Edit-todo-desc Edit-todo-desc
     `Cancel-todo-edit-desc Cancel-todo-edit-desc
     `Delete-todo Delete-todo
     `Toggle-all Toggle-all
     `Create-todo Create-todo
     `Set-delay Set-delay
     `Set-filter Set-filter}))

(e/defn Service [effects edits]
  #_(binding [effects (merge effects effects')])
  (e/client ; client bias, t doesn't transfer
    (e/for [[t [cmd & args]] (e/Filter some? edits)]
      (prn 'cmd (name cmd) args)
      (case (when-some [Effect (get effects cmd)] ; security - rules engine? `(F ~x) ? prevent auditing of imperative adhoc security
              (let [res (e/Apply Effect args)]
                (prn 'res res)
                (case res ::ok ::ok, ::fail ::fail, ::ok)))
        ::fail nil ; idea: (t err) to prompt for retry
        ::ok (t)))))

(def state0 {::filter :all, ::editing nil, ::delay 0})
#?(:clj (defonce !conn (d/create-conn {})))

(e/defn TodoMVC []
  (e/client
    (dom/link (dom/props {:rel :stylesheet, :href "/todomvc.css"}))
    (dom/props {:class "todomvc"})
    (binding [!state (atom state0)]
      (binding [state (e/watch !state)]
        (binding [db (e/server (e/watch !conn))
                  Transact! (e/server (e/Partial Slow-transact (e/client (::delay state)) !conn))]
          (Service (Effects)
            (e/amb
              (TodoMVC-UI db state)
              (Diagnostics db state))))))

    (dom/footer (dom/props {:class "info"})
      (dom/p (dom/text "Double-click to edit a todo")))))

(comment
  (todo-count @!conn :all)
  (todo-count @!conn :active)
  (todo-count @!conn :done)
  (query-todos @!conn :all)
  (query-todos @!conn :active)
  (query-todos @!conn :done)
  (d/q '[:find (count ?e) . :where [?e :task/status]] @!conn))