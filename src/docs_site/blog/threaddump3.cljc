(ns docs-site.blog.threaddump3
  #?(:clj (:import org.eclipse.jgit.api.Git
                   java.lang.management.ThreadMXBean))
  (:require [contrib.data :refer [unqualify]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [dustingetz.entity-browser0 :refer [EntityBrowser0]]
            #?(:clj dustingetz.datafy-git)
            #?(:clj dustingetz.datafy-jvm)
            #?(:clj dustingetz.datafy-fs)))

(e/defn UserResolve [[tag id]]
  (case tag
    :tap (Tap)
    :thread-mx (e/server (dustingetz.datafy-jvm/resolve-thread-manager))
    :thread (e/server (dustingetz.datafy-jvm/resolve-thread id))
    :git (e/server (dustingetz.datafy-git/load-repo id))
    :thread-meta (e/server java.lang.management.ThreadMXBean)
    :git-meta (e/server org.eclipse.jgit.api.Git)
    :file (e/server (clojure.java.io/file (dustingetz.datafy-fs/absolute-path id)))
    (e/amb)))

(declare css)
(e/defn ThreadDump3 []
  (e/client (dom/style (dom/text css)) (dom/props {:class "ThreadDump3"})
    (dom/text "Target: ")
    (e/for [[tag e :as ref] (e/amb [:thread-mx] [:git "./"] [:file "./"] [:git-meta])]
      (r/link ['. [ref]] (dom/text (pr-str (remove nil? [(unqualify tag) e])))))

    (if-not (seq r/route)
      (r/ReplaceState! ['. [[:thread-mx]]])
      (binding [dustingetz.entity-browser0/Resolve UserResolve]
        (e/Apply EntityBrowser0 r/route)))))

(def css "
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")