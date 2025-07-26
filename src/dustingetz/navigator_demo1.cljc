(ns dustingetz.navigator-demo1
  (:require
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.navigator4 :refer [HfqlRoot]]))

#?(:clj (def index
          `[]))

(e/defn NavigatorDemo1 []
  (e/client
    (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/electric-forms.css"}))
    (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/datomic-browser.css"})) ; TODO remove
    (HfqlRoot
      (e/server (merge {})) ; don't externalize, we want hot reload on sitemap change
      index)))