(ns docs-site.blog.threaddump3
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [dustingetz.entity-browser0 :refer [EntityBrowser0]]
            #?(:clj dustingetz.datafy-git2)
            #?(:clj dustingetz.datafy-fs)))

#_#?(:clj (dustingetz.datafy-git/load-repo "./")) ; warm memo cache on startup - optimize blog perf

(declare css)
(e/defn ThreadDump3 []
  (e/client #_(dom/style (dom/text css)) (dom/props {:class "ThreadDump3"})
    (let [x (e/server (dustingetz.datafy-git2/load-repo "../"))]
      (EntityBrowser0 x))))

(def css "
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")
