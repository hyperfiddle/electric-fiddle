(ns dustingetz.object-browser-demo2
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [contrib.data :refer [unqualify map-entry]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric3-contrib :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [dustingetz.entity-browser2 :refer [EntityBrowser2]]
            #?(:clj dustingetz.datafy-git2)
            #?(:clj dustingetz.datafy-jvm2)
            #?(:clj dustingetz.datafy-fs)
            #?(:clj dustingetz.datafy-clj)))

#?(:clj (defn resolve-class [whiteset qs]
          (try (some-> (whiteset qs) resolve) (catch Exception e nil))))

(e/defn UserResolve [[tag id]]
  (e/server
    (case tag
      :tap (Tap)
      :thread-mx (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread-manager))
      :thread (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread id))
      :git (ex/Offload-reset #(dustingetz.datafy-git2/load-repo "./"))
      :class (ex/Offload-reset #(resolve-class #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean} id))
      :file (ex/Offload-reset #(clojure.java.io/file (dustingetz.datafy-fs/absolute-path id)))
      'clojure.core/all-ns (ex/Offload-reset #(vec (sort-by ns-name (all-ns))))
      (e/amb))))

(def targets [[['clojure.core/all-ns] ['clojure.core]] [[:git]] [[:file "./"]] [[:thread-mx]] [[:tap]]
              [[:class 'org.eclipse.jgit.api.Git]]
              [[:class 'java.lang.management.ThreadMXBean]]])

(declare css)
(e/defn ObjectBrowserDemo2 []
  (e/client (dom/style (dom/text css)) (dom/props {:class "ThreadDump3"})
    (dom/text "Target: ")
    (e/for [[[tag e] & paths :as ref] (e/diff-by {} targets)]
      (r/link ['. ref] (dom/text (pr-str (remove nil? [(unqualify tag) e])))))

    (when-some [[uri & _] r/route]
      (r/pop
        (e/for [uri (e/diff-by identity (e/as-vec uri))] ; workaround glitch (Leo, please explain?)
          (EntityBrowser2 (e/server (map-entry uri (UserResolve uri)))))))))

(def css "
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */")