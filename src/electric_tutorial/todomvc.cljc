(ns electric-tutorial.todomvc
  (:require [contrib.data :refer [unqualify]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.cqrs0 :as cqrs :refer [Form Service]]
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
            (Form (Checkbox! (= :done status) :class "toggle" :parse #(hash-map 0 %))
              :commit (fn [dirties]
                        (let [{v 0} (apply merge dirties)
                              status (case v true :done, false :active, nil)]
                          [[`Toggle id status] {}]))
              :show-buttons false :auto-submit true)
            (dom/label (dom/text description)
              (e/for [[t _] (dom/On-all "dblclick" (constantly true))]
                (prn 'click!)
                [t [`Editing-item id] {}]))))
        (when (= id (::editing state))
          (dom/span (dom/props {:class "input-load-mask"})
            (Form (Input! description :class "edit" :autofocus true :parse #(hash-map 0 %))
              :commit (fn [dirties]
                        (let [{v 0} (apply merge dirties)]
                          [[`Edit-todo-desc id v] {}]))
              :discard `[[Cancel-todo-edit-desc] {}] ; does guess need the dirties?
              :show-buttons false)))
        (Button! [`Delete-todo id] :class "destroy"))))) ; missing prediction

(e/defn Query-todos [db filter edits]
  (e/server (e/diff-by identity (sort (query-todos db filter)))))

(e/defn TodoList [db state edits]
  (dom/div
    (dom/section (dom/props {:class "main"})
      (e/amb
        (let [active (e/server (todo-count db :active))
              all    (e/server (todo-count db :all))
              done   (e/server (todo-count db :done))]
          (Form (Checkbox! (cond (= all done) true (= all active) false :else nil)
                  :class "toggle-all" :parse #(hash-map 0 %))
            :commit (fn [dirties]
                      (let [{v 0} (apply merge dirties)
                            status (case v (true nil) :done, false :active)]
                        [[`Toggle-all status] {}]))
            :auto-submit true :show-buttons false))
        (dom/label (dom/props {:for "toggle-all"}) (dom/text "Mark all as complete"))
        (dom/ul (dom/props {:class "todo-list"})
          (e/for [id (Query-todos db (::filter state) edits)]
            (TodoItem db state id)))))))

(e/defn CreateTodo []
  (dom/span (dom/props {:class "input-load-mask"})
    #_(Form :show-buttons false) ; todo?
    (e/for [[t v] (InputSubmitCreate! :class "new-todo input-load-mask"
                    :placeholder "What needs to be done?")]
      [t [`Create-todo v] {}])))

(e/defn TodoMVC-UI [db state edits]
  (dom/section (dom/props {:class "todoapp"})
    (e/amb
      (dom/header (dom/props {:class "header"})
        (CreateTodo))
      (e/When (e/server (pos? (todo-count db :all)))
        (TodoList db state edits))
      (dom/footer (dom/props {:class "footer"})
        (TodoStats db state)))))

(e/defn Diagnostics [db state]
  (dom/dl
    (dom/dt (dom/text "count :all")) (dom/dd (dom/text (pr-str (e/server (todo-count db :all)))))
    (dom/dt (dom/text "query :all")) (dom/dd (dom/text (pr-str (e/server (query-todos db :all)))))
    (dom/dt (dom/text "state")) (dom/dd (dom/text (pr-str (update-keys state unqualify))))
    (dom/dt (dom/text "delay")) (dom/dd (e/amb (Input! (::delay state)
                                                 :parse (fn [v] [`Set-delay (parse-long v)]) ; can bypass form bc no dirty|retry state
                                                 :type "number" :step 1 :min 0
                                                 :style {:width :min-content})
                                          (dom/text " ms")))))

(e/defn Transact! [!conn delay tx]
  (e/server (prn 'Transact! delay tx)
    (e/Offload #(try (Thread/sleep delay)
                  (d/transact! !conn tx) (doto ::cqrs/ok prn)
                  (catch Throwable e (doto ::fail (prn e)))))))

(def db nil)
(def !state nil)
(def state nil)

(e/defn Clear-completed []
  (e/server (->> (seq (query-todos db :done))
              (mapv (fn [id] [:db/retractEntity id])) Transact!)))

(e/defn Toggle [id status]
  (e/server (Transact! [{:db/id id, :task/status status}])))

(e/defn Toggle-all [status]
  (e/server (Transact! (->> (query-todos db (if (= :done status) :active :done))
                         (mapv (fn [id] {:db/id id, :task/status status}))))))

(e/defn Cancel-todo-edit-desc [] (e/client (swap! !state assoc ::editing nil) ::cqrs/ok))
(e/defn Delete-todo [id] (e/server (Transact! [[:db/retractEntity id]])))
(e/defn Create-todo [desc] (e/server (Transact! [{:task/description desc, :task/status :active}])))
(e/defn Editing-item [id] (e/client (swap! !state assoc ::editing id) ::cqrs/ok))
(e/defn Edit-todo-desc [id desc]
  (e/client (case (e/server (Transact! [{:db/id id, :task/description desc}]))
              ::cqrs/ok (do (swap! !state assoc ::editing nil) ::cqrs/ok)
              ::fail)))

(e/defn Set-delay [v] (e/client (swap! !state assoc ::delay v) ::cqrs/ok))
(e/defn Set-filter [target] (e/client (swap! !state assoc ::filter target) ::cqrs/ok))

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
                  Transact! (e/server (e/Partial Transact! !conn (e/client (::delay state))))]
          (Service (Effects)
            (e/amb
              (e/with-cycle* first [edits (e/amb)]
                (e/Filter some?
                  (TodoMVC-UI db state edits)))
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