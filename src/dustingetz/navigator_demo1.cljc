(ns dustingetz.navigator-demo1
  (:require
    dustingetz.nav-jvm
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
      (e/server (merge dustingetz.nav-jvm/sitemap)) ; for hot reload, don't externalize a def
      index)))