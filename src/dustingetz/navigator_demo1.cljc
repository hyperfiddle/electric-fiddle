(ns dustingetz.navigator-demo1
  (:require
    #?(:clj [dustingetz.nav-jar :as jar])
    #?(:clj [dustingetz.nav-jvm :as jvm])
    #?(:clj [dustingetz.nav-git :as git])
    #?(:clj [dustingetz.nav-hn :as hn])
    #?(:clj [dustingetz.nav-aws :as aws])
    ;#?(:clj [dustingetz.nav-py :as py]) ; works
    #?(:clj [dustingetz.nav-clojure-ns :as cljns])
    #?(:clj [dustingetz.codeq-model :as q])
    #?(:clj [dustingetz.nav-auth0 :as auth0])
    #?(:clj [dustingetz.nav-deps :as deps])
    #?(:clj [dustingetz.nav-twitter :as twitter])
    #?(:clj [dustingetz.nav-kondo :as kondo])
    #?(:clj [dustingetz.nav-reddit :as reddit])
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]
    [hyperfiddle.navigator4 :refer [HfqlRoot]]))

#?(:clj (def index
          `[(git/load-repo ~git/git-repo-path)]))

#?(:clj (def sitemap
          (hfql/sitemap
            {all-ns (hfql/props [ns-name] {::hfql/select (cljns/ns-publics2 %)})
             cljns/ns-publics2 [symbol]
             kondo/kondo []
             deps/deps-project []
             deps/mvn [.getUrl .getContentType .getAuthentication]
             jar/list-project-jars [jar/jar-filename]
             git/load-repo []
             ;q/list-ns-decls [] ; codeq - depends on special datomic database
             ;q/list-var-codeqs []

             jvm/getThreadMXBean [jvm/getAllThreads]
             jvm/getMemoryMXBean []
             jvm/getRuntimeMXBean []
             jvm/getOperatingSystemMXBean []

             auth0/auth0 [auth0/users]
             reddit/me [.getUsername .listMultis .karma .trophies .about .prefs]
             reddit/me-about [.getName .getLinkKarma .getCommentKarma]
             twitter/twitter [(hfql/props .tweets {::hfql/select (twitter/tweets %)})
                              (hfql/props .users {::hfql/select (twitter/users %)})]

             ;hn/hn [type hn/topstories hn/beststories hn/newstories] ; works but very slow

             aws/aws-s3-us-east [aws/list-buckets aws/aws-ops]

             ;py/py-hello-world []
             ;py/py-environ []
             ;py/py-os [py/cpu-count py/dir]
             ;py/py-platform [py/dir]
             })))

(e/defn NavigatorDemo1 []
  (e/client
    (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/electric-forms.css"}))
    (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/datomic-browser.css"})) ; TODO remove
    (HfqlRoot
      (e/server (merge sitemap))
      index)))
