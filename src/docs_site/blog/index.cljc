(ns docs-site.blog.index
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [electric-essay.tutorial-app :as t :refer [index-essay-index title]]))

(e/defn BlogIndex [sitemap]
  (e/client
    (let [essay-index (index-essay-index sitemap)]
      (e/for [[group-label entries] (e/diff-by {} sitemap)]
        (dom/h1 (dom/props {:label group-label})) ; group
        (dom/ul
          (e/for [page (e/diff-by {} entries)]
            (let [m (essay-index page)]
              (dom/li (r/link ['.. [page]] (dom/text (title m)))))))))))