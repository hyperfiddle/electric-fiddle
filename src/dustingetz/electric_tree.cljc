(ns dustingetz.electric-tree
  (:require [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(def css "
.dustingetz-electric-tree ul {list-style: none; display: inline-flex;}
.dustingetz-electric-tree li {list-style: none; margin: 0; padding: 0;}

.dustingetz-electric-tree {
    font-family: monospace;
    font-size: 1rem;
    position: relative;
    gap: 0 8px; }

.dustingetz-electric-tree ul:hover,
.dustingetz-electric-tree li:hover { background: rgba(200,200,200,0.2); }

.dustingetz-electric-tree ul[aria-expanded=\"true\"],
.dustingetz-electric-tree li[aria-expanded=\"true\"] { flex-direction: column; }")

(declare MapView CollView)

(e/defn View [data]
  (cond
    (map? data) (MapView data)
    (coll? data) (CollView data)
    :else (dom/text (pr-str data))))

(e/defn CollView [data]
  (let [[begin end] (cond
                      (vector? data) "[]"
                      (set? data) ["#{" "}"]
                      :else "()")
        *expanded? (atom false)]
    (dom/li (dom/props {:role "treeitem"})
      (dom/On "click" #(do (prn %) (.stopPropagation %) (swap! *expanded? not)))
      (dom/ul (dom/props {:role "group" :aria-expanded (e/watch *expanded?)})
        (dom/text begin)
        (e/for [x (e/diff-by identity data)]
          (dom/li (dom/props {:role "treeitem"})
            (View x)))
        (dom/text end)))))

(e/defn MapEntryView [k v]
  (dom/li (dom/props {:role "treeitem"})
    (dom/ul (dom/props {:role "group"})
      (View k)
      (dom/text " ")
      (View v))))

(e/defn MapView [data]
  (let [*expanded? (atom false)]
    (dom/li (dom/props {:role "treeitem"})
      (dom/On "click" #(do (prn %) (.stopPropagation %) (swap! *expanded? not)))
      (dom/ul (dom/props {:role "group" :aria-expanded (e/watch *expanded?)})
        (dom/text "{")
        (e/for [kv (e/diff-by key data)]
          (MapEntryView (key kv) (val kv)))
        (dom/text "}")))))

(def tree '{:deps     true
            :builds   {:app nil
                       #_ ; this structure breaks the app fixme
                       {:target     :browser
                        :asset-path "/js"
                        :output-dir "resources/public/js"
                        :modules    {:main {:init-fn user/start!
                                            :entries [user]}}
                        :devtools   {:watch-dir       "resources/public"
                                     :hud             #{:errors :progress}
                                     :ignore-warnings true}}}
            :dev-http {8080 "resources/public"}
            :nrepl    {:port 9001}
            :npm-deps {:install false}})

(e/defn TreeDemo []
  (e/client
    #_(dom/li (dom/props {:role "treeitem"})
        (dom/On "click" prn))
    (dom/h1 (dom/text "Tree view"))
    (dom/style (dom/text css))
    (dom/div (dom/props {:class "dustingetz-electric-tree"})
      (dom/ul (dom/props {:role "tree"})
        (View tree)))))
