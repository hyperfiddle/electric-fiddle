(ns dustingetz.essay
  (:require clojure.string
            [electric-fiddle.fiddle-index :refer [FiddleIndex pages]]
            [electric-fiddle.fiddle-markdown :refer [Custom-markdown]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]))

(def fiddle-root "src/dustingetz/")

(def essays
  {'hello "hello.md"
   'electric-y-combinator "electric_y_combinator.md"
   'hfql-intro "hfql_intro.md"
   'hfql-teeshirt-orders "hfql_teeshirt_orders.md"})

(e/defn Extensions []
  {'fiddle electric-fiddle.fiddle-markdown/Fn ; compat
   'fiddle-ns electric-fiddle.fiddle-markdown/Ns ; compat
   'fn electric-fiddle.fiddle-markdown/Fn
   'ns electric-fiddle.fiddle-markdown/Ns})

(e/defn Essay []
  #_(e/client (dom/div #_(dom/props {:class ""}))) ; fix css grid next
  (e/client
    (let [[?essay] (ffirst r/route) ; wut
          essay-filename (get essays ?essay)]
      (cond
        (nil? ?essay) (binding [pages essays] (FiddleIndex))
        (nil? essay-filename) (dom/h1 (dom/text "Essay not found: " ?essay))
        () (Custom-markdown (Extensions) (str fiddle-root essay-filename))))))
