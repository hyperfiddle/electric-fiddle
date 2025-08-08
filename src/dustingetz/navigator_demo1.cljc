(ns dustingetz.navigator-demo1
  (:require
    dustingetz.nav-jar
    dustingetz.nav-jvm
    dustingetz.nav-git
    dustingetz.nav-hn
    dustingetz.nav-aws
    #_dustingetz.nav-py
    dustingetz.nav-clojure-ns
    #?(:clj [dustingetz.nav-auth0 :as auth0])
    #?(:clj [dustingetz.nav-twitter :as twitter])
    #?(:clj [dustingetz.nav-kondo :as kondo])
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]
    [hyperfiddle.navigator4 :refer [HfqlRoot]]))

#?(:clj (def index
          `[]))

#?(:clj (def sitemap
          (hfql/sitemap
            {auth0/auth0 [auth0/users]
             twitter/twitter [(hfql/props .tweets {::hfql/select (twitter/tweets %)})
                              (hfql/props .users {::hfql/select (twitter/users %)})]
             twitter/tweets []
             twitter/users []
             kondo/kondo []
             })))

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
                  dustingetz.nav-aws/sitemap
                  #_dustingetz.nav-py/sitemap
                  dustingetz.nav-clojure-ns/sitemap
                  sitemap))
      index)))
