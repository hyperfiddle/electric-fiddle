(ns dustingetz.navigator-demo1
  (:require
    dustingetz.nav-jar
    dustingetz.nav-jvm
    dustingetz.nav-git
    dustingetz.nav-hn
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
      (e/server (merge ; for hot reload, don't externalize a def
                  dustingetz.nav-jar/sitemap
                  dustingetz.nav-jvm/sitemap
                  dustingetz.nav-git/sitemap
                  dustingetz.nav-hn/sitemap
                  ))
      index)))