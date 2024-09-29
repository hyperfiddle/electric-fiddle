(ns electric-tutorial.todomvc
  (:require [contrib.data :refer [unqualify]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :refer [Form]]
            [hyperfiddle.input-zoo0 :refer
             [Input! Checkbox! InputSubmitCreate! Button!]]))

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
  (Button! [`Set-filter target] :label label
    :class (str "anchor-button " (when (= state target) "selected"))))

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
              [t [`Clear-completed] {}])))))))

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
            (Form
              (e/for [[t v] (Checkbox! (= :done status) :class "toggle")]
                (let [status (case v true :done, false :active, nil)]
                  [t [`Toggle id status] {}]))
              :show-buttons false :auto-submit true)
            (dom/label (dom/text description)
              (e/for [[t _] (dom/On-all "dblclick" (constantly true))]
                [t [`Editing-item id] {}]))))
        (when (= id (::editing state))
          (dom/span (dom/props {:class "input-load-mask"})
            (Form (e/for [[t v] (Input! description :class "edit" :autofocus true)]
                    [t [`Edit-todo-desc id v] {}])
              :discard `[[Cancel-todo-edit-desc] {}]
              :show-buttons false)))
        (Button! [`Delete-todo id] :class "destroy"))))) ; missing prediction

(e/defn Query-todos [db filter]
  (e/server (e/diff-by identity (sort (query-todos db filter)))))

(e/defn TodoList [db state]
  (dom/div
    (dom/section (dom/props {:class "main"})
      (e/amb
        (let [active (e/server (todo-count db :active))
              all    (e/server (todo-count db :all))
              done   (e/server (todo-count db :done))]
          (Form
            (e/for [[t v] (Checkbox! (cond (= all done) true (= all active) false :else nil)
                            :class "toggle-all")]
              (let [status (case v (true nil) :done, false :active)]
                [t [`Toggle-all status] {}]))
            :auto-submit true :show-buttons false))
        (dom/label (dom/props {:for "toggle-all"}) (dom/text "Mark all as complete"))
        (dom/ul (dom/props {:class "todo-list"})
          (e/for [id (Query-todos db (::filter state))]
            (TodoItem db state id)))))))

(e/defn CreateTodo []
  (dom/span (dom/props {:class "input-load-mask"})
    (e/for [[t v] (InputSubmitCreate! :class "new-todo input-load-mask"
                    :placeholder "What needs to be done?")]
      [t [`Create-todo v] {}])))

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
                                                 ; no form needed because there's no dirty/retry state
                                                 [t [`Set-delay (parse-long v)]])
                                          (dom/text " ms")))))

(e/defn Slow-transact [delay !conn tx]
  (e/server (e/Offload #(try (Thread/sleep delay) #_(assert false "die") (d/transact! !conn tx) ::ok
                          (catch InterruptedException _) ; never seen
                          (catch Throwable e (doto ::fail (prn e)))))))

(def Transact! nil)
(def db nil)
(def !state nil)
(def state nil)

(e/defn Clear-completed []
  (e/server (->> (seq (query-todos db :done))
              (mapv (fn [id] [:db/retractEntity id])) Transact!)))

(e/defn Toggle [id status]
  (e/client (case (e/server (Transact! [{:db/id id, :task/status status}]))
              ::ok (do (swap! !state assoc ::editing nil) ::ok)
              ::fail)))

(e/defn Toggle-all [status]
  (e/server (Transact! (->> (query-todos db (if (= :done status) :active :done))
                         (mapv (fn [id] {:db/id id, :task/status status}))))))

(e/defn Cancel-todo-edit-desc [] (e/client (swap! !state assoc ::editing nil) ::ok))
(e/defn Delete-todo [id] (e/server (Transact! [[:db/retractEntity id]])))
(e/defn Create-todo [desc] (e/server (Transact! [{:task/description desc, :task/status :active}])))
(e/defn Editing-item [id] (e/client (swap! !state assoc ::editing id) ::ok))
(e/defn Edit-todo-desc [id desc]
  (e/client (case (e/server (Transact! [{:db/id id, :task/description desc}]))
              ::ok (do (swap! !state assoc ::editing nil) ::ok)
              ::fail)))

(e/defn Set-delay [v] (e/client (swap! !state assoc ::delay v) ::ok))
(e/defn Set-filter [target] (e/client (swap! !state assoc ::filter target) ::ok))

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

(e/defn Service [effects txs]
  #_(binding [effects (merge effects effects')])
  (e/client
    (prn (e/Count txs) 'txs)
    (e/for [[t [cmd & args]] (e/Filter some? txs)] ; cmds
      (prn 'cmd (name cmd) args)
      (let [res (when-some [Effect (get effects cmd)] ; security - rules engine? `(F ~x) ? prevent auditing of imperative adhoc security
                  (e/Apply Effect args))]
        (case res
          ::ok (t)
          (t ::rejected))))))

(def state0 {::filter :all, ::editing nil, ::delay 500})
#?(:clj (def !conn (doto (d/create-conn {})
                     (d/transact!
                       [{:task/description "feed baby" :task/status :active}
                        {:task/description "buy milk" :task/status :active}
                        {:task/description "call mom" :task/status :active}]))))

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