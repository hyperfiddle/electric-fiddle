(ns electric-tutorial.todomvc
  "Requires -Xss2m to compile. The Electric compiler exceeds the default 1m JVM ThreadStackSize
  due to large macroexpansion resulting in false StackOverflowError during analysis."
  (:require [contrib.str :refer [blank->nil]]
            #?(:clj [datascript.core :as d])
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-tutorial.forms :refer [Checkbox!]]
            [electric-tutorial.temperature :refer [Input]]
            [missionary.core :as m]))

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
      (e/for [[t _] (dom/OnAll "click" (constantly true))]
        [t ::set-filter target]))))

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
            (e/for [[t _] (dom/OnAll "click" (constantly true))]
              [t ::clear-completed])))))))

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
            (PendingMonitor
              (e/for [[t v] (Checkbox! (= :done status) :class "toggle")]
                (let [status (case v true :done, false :active, nil)]
                  [t ::toggle id status])))
            (dom/label (dom/text description)
              (e/for [[t _] (dom/OnAll "dblclick" (constantly true))]
                [t ::editing-item id]))))
        (when (= id (::editing state))
          (dom/span (dom/props {:class "input-load-mask"})
            (dom/input (dom/props {:class "edit" #_#_:autofocus true})
              #_(dom/bind-value description) ; first set the initial value, then focus
              (set! (.-value dom/node) description)
              #_(case description (.focus dom/node)) ; don't focus until description is available
              (PendingMonitor
                (e/for [[t e] (dom/OnAll "keydown" identity)]
                  (case (.-key e)
                    "Enter" (when-some [desc (blank->nil (-> e .-target .-value))]
                              [t ::edit-todo-desc id desc])
                    "Escape" [t ::cancel-todo-edit-desc]
                    (e/amb)))))))
        (dom/button (dom/props {:class "destroy"})
          (PendingMonitor
            (e/for [[t _] (dom/OnAll "click" (constantly true))]
              [t ::delete-todo id])))))))

(e/defn Query-todos [db filter]
  (e/server (e/diff-by identity (sort (query-todos db filter)))))

(e/defn TodoList [db state]
  (dom/div
    (dom/section (dom/props {:class "main"})
      (e/amb
        (let [active (e/server (todo-count db :active))
              all    (e/server (todo-count db :all))
              done   (e/server (todo-count db :done))]
          (PendingMonitor
            (e/for [[t v] (Checkbox! (cond (= all done) true (= all active) false :else nil)
                            :class "toggle-all")]
              (let [status (case v (true nil) :done, false :active)]
                [t ::toggle-all status]))))
        (dom/label (dom/props {:for "toggle-all"}) (dom/text "Mark all as complete"))
        (dom/ul (dom/props {:class "todo-list"})
          (e/for [id (Query-todos db (::filter state))]
            (TodoItem db state id)))))))

(e/defn CreateTodo []
  (dom/span (dom/props {:class "input-load-mask"})
    (PendingMonitor
      (dom/input (dom/props {:class "new-todo", :placeholder "What needs to be done?"})
        (e/for [[t e] (dom/OnAll "keydown" identity)]
          (e/When (= "Enter" (.-key e))
            (if-some [desc (not-empty (-> e .-target .-value))]
              (do (set! (.-value dom/node) "") [t ::create-todo desc])
              (e/amb))))))))

(e/defn TodoMVC-UI [db state]
  (dom/section (dom/props {:class "todoapp"})
    (e/amb
      (dom/header (dom/props {:class "header"})
        (CreateTodo))
      (e/When (e/server (pos? (todo-count db :all)))
        (TodoList db state))
      (dom/footer (dom/props {:class "footer"})
        (TodoStats db state)))))

(e/defn TodoMVC-body [db state]
  (dom/div (dom/props {:class "todomvc"})
    (e/amb
      (TodoMVC-UI db state)
      (dom/footer (dom/props {:class "info"})
        (dom/p (dom/text "Double-click to edit a todo"))))))

(e/defn Diagnostics [db state]
  (dom/h1 (dom/text "Diagnostics"))
  (dom/dl
    (dom/dt (dom/text "count :all")) (dom/dd (dom/text (pr-str (e/server (todo-count db :all)))))
    (dom/dt (dom/text "query :all")) (dom/dd (dom/text (pr-str (e/server (query-todos db :all)))))
    (dom/dt (dom/text "state")) (dom/dd (dom/text (pr-str state)))
    (dom/dt (dom/text "delay")) (dom/dd (some->> (Input (::delay state)
                                                   :type "number" :step 1 :min 0
                                                   :style {:width :min-content})
                                          not-empty parse-long [(fn [_]) ::set-delay])
                                  (dom/text " ms"))))

#?(:clj (defn slow-transact [delay !conn tx]
          (m/via m/blk
            (try (Thread/sleep delay) ; artificial latency
              (d/transact! !conn tx)
              (catch InterruptedException _)))))

#?(:clj (defn effects [[cmd & args] !conn db !state state]
          (let [transact! (partial slow-transact (::delay state) !conn)]
            (case cmd
              ::clear-completed
              (m/sp (let [tx (->> (seq (query-todos db :done))
                               (mapv (fn [id] [:db/retractEntity id])))]
                      (transact! tx)))
              ::toggle
              (m/sp (let [[id status] args]
                      (transact! [{:db/id id, :task/status status}])
                      (swap! !state assoc ::editing nil)))
              ::editing-item
              (m/sp (let [[id] args]
                      (swap! !state assoc ::editing id)))
              ::edit-todo-desc
              (m/sp (let [[id desc] args]
                      (transact! [{:db/id id, :task/description desc}])
                      (swap! !state assoc ::editing nil)))
              ::cancel-todo-edit-desc
              (m/sp (swap! !state assoc ::editing nil))
              ::delete-todo
              (m/sp (let [[id] args]
                      (transact! [[:db/retractEntity id]])))
              ::toggle-all
              (m/sp (let [[status] args]
                      (transact! (->> (query-todos db (if (= :done status) :active :done))
                                   (mapv (fn [id] {:db/id id, :task/status status}))))))
              ::create-todo
              (m/sp (let [[desc] args]
                      (transact! [{:task/description desc, :task/status :active}])))

              ::set-delay (m/sp (let [[v] args] (swap! !state assoc ::delay v)))
              ::set-filter (m/sp (let [[target] args] (swap! !state assoc ::filter target)))

              ))))

#?(:clj (defonce !conn (d/create-conn {})))
#?(:cljs (def !state (atom {::filter :all
                            ::editing nil
                            ::delay   0})))

(e/defn TodoMVC []
  (e/client
    ; exclude #root style from todomvc-composed by inlining here
    (dom/link (dom/props {:rel :stylesheet, :href "/todomvc.css"}))
    (let [state (e/watch !state)
          db (e/server (e/watch !conn))]
      (e/for [[t & xcmd] (TodoMVC-body db state)] ; client bias, t doesn't transfer
        (case (e/server ; secure
                (when-some [effect (binding []
                                     (effects xcmd !conn db !state state))]
                  (case (e/Task effect) ::ok)))
          ::ok (t)))
      (Diagnostics db state))))

(comment
  (todo-count @!conn :all)
  (todo-count @!conn :active)
  (todo-count @!conn :done)
  (query-todos @!conn :all)
  (query-todos @!conn :active)
  (query-todos @!conn :done)
  (d/q '[:find (count ?e) . :where [?e :task/status]] @!conn))