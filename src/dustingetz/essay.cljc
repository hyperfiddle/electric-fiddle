(ns dustingetz.essay
  (:require clojure.string
            [electric-fiddle.fiddle :refer [Fiddle-fn Fiddle-ns]]
            [electric-fiddle.fiddle-markdown :refer [Custom-markdown]]
            [electric-fiddle.index :refer [Index]]
            [hyperfiddle :as hf]
            [hyperfiddle.electric-de :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router-de :as r]))

(def fiddle-root "src/dustingetz/")

(def essays
  {'hello "hello.md"
   'electric-y-combinator "electric_y_combinator.md"
   'hfql-intro "hfql_intro.md"
   'hfql-teeshirt-orders "hfql_teeshirt_orders.md"})

(e/defn Extensions []
  {'fiddle Fiddle-fn
   'fiddle-ns Fiddle-ns})

(e/defn Essay []
  #_(e/client (dom/div #_(dom/props {:class ""}))) ; fix css grid next
  (e/client
    (let [[?essay] (ffirst r/route) ; wut
          essay-filename (get essays ?essay)]
      (cond
        (nil? ?essay) (binding [hf/pages essays] (Index))
        (nil? essay-filename) (dom/h1 (dom/text "Essay not found: " ?essay))
        () (Custom-markdown (Extensions) (str fiddle-root essay-filename))))))
