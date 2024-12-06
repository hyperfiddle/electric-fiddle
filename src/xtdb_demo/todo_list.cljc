(ns xtdb-demo.todo-list
  (:require
   #?(:clj [xtdb-demo.xtdb-contrib :as db])
   [hyperfiddle.electric3 :as e]
   [hyperfiddle.electric-dom3 :as dom]
   [xtdb.api #?(:clj :as :cljs :as-alias) xt]
   [missionary.core :as m]))

(def !xtdb)
(def db) ; injected database ref; Electric defs are always dynamic

#?(:clj (defn submit-tx [!xtdb tx-data] (m/sp (xt/submit-tx !xtdb tx-data))))

(e/defn TodoItem [id]
  (let [e (e/server (xt/entity db id))]
    (dom/div
      (dom/input (dom/props {:type :checkbox})
                 (set! (.-checked dom/node) (e/server (case (:task/status e) :active false, :done true)))
                 (let [value (dom/On "change" (fn [event] (-> event .-target .-checked)) nil)]
                   (when-let [spend! (e/TokenNofail value)]
                     (spend! (e/server (e/Task (submit-tx !xtdb [[:xtdb.api/put
                                                                  {:xt/id            id
                                                                   :task/description (:task/description e) ; repeat
                                                                   :task/status      (if value :done :active)}]])))))))
      (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (e/client
    (dom/input (dom/props {:placeholder "Buy milk"})
      (e/for [[value spend!] (dom/On-all "keydown" (fn [event]
                                                    (let [value (-> event .-target .-value)]
                                                      (when (and (= "Enter" (.-key event)) (not-empty value))
                                                        (set! (.-value dom/node) "")
                                                        value))))]
        (spend! (e/call F value))))))

(e/defn TodoCreate []
  (InputSubmit (e/fn [value]
                 (e/server (e/Task (submit-tx !xtdb [[:xtdb.api/put
                                                      {:xt/id (random-uuid)
                                                       :task/description value
                                                       :task/status :active}]]))))))

#?(:clj
   (defn todo-records [db]
     (->> (xt/q db '{:find [(pull ?e [:xt/id :task/description])]
                     :where [[?e :task/status]]})
       (map first)
       (sort-by :task/description)
       vec)))

#?(:clj
   (defn todo-count [db]
     (count (xt/q db '{:find [?e] :in [$ ?status]
                       :where [[?e :task/status ?status]]}
              :active))))

(e/defn Todo-list [!xtdb]
  (binding [xtdb-demo.todo-list/!xtdb (e/server !xtdb)
            db (e/server (e/input (db/latest-db> !xtdb)))]
    (dom/h1 (dom/text "minimal todo list"))
    (dom/p (dom/text "it's multiplayer, try two tabs"))
    (dom/div
      (dom/props {:class "todo-list"})
      (TodoCreate)
      (dom/div {:class "todo-items"}
               (e/for [{:keys [xt/id]} (e/server (e/diff-by :xt/id (e/Offload #(todo-records db))))]
                 (TodoItem id)))
      (dom/p (dom/props {:class "counter"})
             (dom/span (dom/props {:class "count"})
                       (dom/text (e/server (e/Offload #(todo-count db)))))
             (dom/text " items left")))))
