(ns docs-site.blog.threaddump2
  #?(:clj (:import [java.lang.management ManagementFactory]
                   org.eclipse.jgit.api.Git
                   java.lang.management.ThreadMXBean))
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            #?(:clj dustingetz.datafy-git)
            #?(:clj dustingetz.datafy-jvm)
            [hyperfiddle.electric-forms0 :refer [Checkbox]]
            [hyperfiddle.router3 :as r]
            [dustingetz.edn-viewer0 :refer [EdnViewer0]]))

#?(:clj (def !git (delay (dustingetz.datafy-git/load-repo "./"))))

(e/defn ThreadDump2 []
  (e/client
    (let [!selected (atom :thread) selected (e/watch !selected)]
      (dom/text "Target: ")
      (e/for [x (e/amb :thread :thread-meta :git :git-meta)]
        (when (Checkbox (= selected x) :label (str x) :type "radio")
          (case (r/ReplaceState! ['. [nil]]) ; hack - clear route first, child paths not valid
            (reset! !selected x))))

      (EdnViewer0
        (e/server
          (case selected
            :thread (ManagementFactory/getThreadMXBean)
            :thread-meta java.lang.management.ThreadMXBean
            :git @!git
            :git-meta org.eclipse.jgit.api.Git
            (e/amb)))))))