(ns docs-site.blog.threaddump3
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [dustingetz.entity-browser4 :refer [HfqlRoot]]
            #?(:clj [clojure.java.io :as io])
            #?(:clj dustingetz.datafy-git2)
            #?(:clj dustingetz.datafy-fs)))

;; #?(:clj (dustingetz.datafy-git/load-repo "./")) ; warm memo cache on startup - optimize blog perf

#?(:clj (defn file-exists? [path] (.exists (clojure.java.io/file path))))
#?(:clj (defn load-repo [] (dustingetz.datafy-git2/load-repo (first (filter file-exists? ["./.git" "../.git"]))))) ; load repo here or in parent folder

(declare css)
(e/defn ThreadDump3 []
  (e/client (dom/style (dom/text css)) (dom/props {:class "ThreadDump3"})
    (binding [dustingetz.entity-browser4/*hfql-bindings (e/server {})] ; cannot be seen by user - not good enough for blogpost
      (HfqlRoot {`(load-repo) []} `[(load-repo)])))) ; not good enough for blogpost - must improve naming and args are not user friendly.

(def css "
 .ThreadDump3 .hyperfiddle-electric-forms5__table-picker { min-height: calc(10 * var(--row-height)); }
")
