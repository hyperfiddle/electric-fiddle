(ns docs-site.blog.threaddump3
  #?(:clj (:import org.eclipse.jgit.api.Git
                   java.lang.management.ThreadMXBean))
  (:require [contrib.data :refer [unqualify]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router3 :as r]
            [dustingetz.entity-browser0 :refer [EntityBrowser0]]
            #?(:clj dustingetz.datafy-git)
            #?(:clj dustingetz.datafy-jvm)))

(e/defn UserResolve [[tag id]]
  (case tag
    :tap (Tap)
    :thread-mx (e/server (dustingetz.datafy-jvm/resolve-thread-manager))
    :thread (e/server (dustingetz.datafy-jvm/resolve-thread id))
    :git (e/server (dustingetz.datafy-git/load-repo id))
    :thread-meta (e/server java.lang.management.ThreadMXBean)
    :git-meta (e/server org.eclipse.jgit.api.Git)
    (e/amb)))

(declare css)
(e/defn ThreadDump3 []
  (e/client (dom/style (dom/text css)) (dom/props {:class "ThreadDump3"})
    (dom/text "Target: ")
    (e/for [[tag e :as ref] (e/amb [:thread-mx] [:thread-meta] [:git "./"] [:git-meta])]
      (r/link ['. [ref]] (dom/text (pr-str (remove nil? [(unqualify tag) e])))))

    (binding [dustingetz.entity-browser0/Resolve UserResolve]
      (r/Apply EntityBrowser0 [[:thread-mx]]))))

(def css "
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")