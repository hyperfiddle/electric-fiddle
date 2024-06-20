(ns hello-fiddle.two-todos
  (:require
   [contrib.debug]
   [contrib.str]
   [datascript.core :as d]
   [hello-fiddle.todo-style]
   [hello-fiddle.todo56-staged2 :as todo]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hello-fiddle.db2 :as db]))

(e/defn TwoTodos []
  (e/server
    (binding [db/conn (d/create-conn), db/!db (atom nil)]
      (reset! db/!db (e/watch db/conn))
      (binding [db/db (e/watch db/!db)]
        (e/client
          (dom/div
            (dom/props {:style {:display "flex"}})
            (db/branch
              (e/fn []
                (dom/div (db/branch todo/App))
                (dom/div (db/branch todo/App))
                (new db/StageUI [])
                [])))
          (hello-fiddle.todo-style/Style.)
          nil)))))
