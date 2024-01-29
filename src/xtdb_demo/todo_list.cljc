(ns xtdb-demo.todo-list
  (:require
   #?(:clj [xtdb-demo.xtdb-contrib :as db])
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui]
   [xtdb.api #?(:clj :as :cljs :as-alias) xt]))

(e/def !xtdb)
(e/def db) ; injected database ref; Electric defs are always dynamic

(e/defn TodoItem [id]
  (e/server
    (let [e (xt/entity db id)
          status (:task/status e)]
      (e/client
        (dom/div
          (ui/checkbox
            (case status :active false, :done true)
            (e/fn [v]
              (e/server
                (e/discard
                  (e/offload
                    #(xt/submit-tx !xtdb [[:xtdb.api/put
                                           {:xt/id id
                                            :task/description (:task/description e) ; repeat
                                            :task/status (if v :done :active)}]])))))
            (dom/props {:id id}))
          (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (e/client
    (dom/input (dom/props {:placeholder "Buy milk"})
      (dom/on "keydown" (e/fn [e]
                          (when (= "Enter" (.-key e))
                            (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                              (new F v)
                              (set! (.-value dom/node) ""))))))))

(e/defn TodoCreate []
  (e/client
    (InputSubmit. (e/fn [v]
                    (e/server
                      (e/discard
                        (e/offload
                          #(xt/submit-tx !xtdb [[:xtdb.api/put
                                                 {:xt/id (random-uuid)
                                                  :task/description v
                                                  :task/status :active}]]))))))))

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
  (e/server
    (binding [xtdb-demo.todo-list/!xtdb !xtdb
              db (new (db/latest-db> !xtdb))]
      (e/client
        (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
        (dom/h1 (dom/text "minimal todo list"))
        (dom/p (dom/text "it's multiplayer, try two tabs"))
        (dom/div (dom/props {:class "todo-list"})
          (TodoCreate.)
          (dom/div {:class "todo-items"}
            (e/server
              (e/for-by :xt/id [{:keys [xt/id]} (e/offload #(todo-records db))]
                (TodoItem. id))))
          (dom/p (dom/props {:class "counter"})
            (dom/span (dom/props {:class "count"})
              (dom/text (e/server (e/offload #(todo-count db)))))
            (dom/text " items left")))))))
