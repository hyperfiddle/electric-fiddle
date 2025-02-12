(ns dustingetz.object-browser-demo2
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [contrib.data :refer [map-entry]]
            [dustingetz.entity-browser1 :refer [HfqlRoot *hfql-spec]]
            [dustingetz.entity-browser2 :refer
             [TableBlock TreeBlock TreeBlock2 Render
              EntityBrowser2]]
            #?(:clj dustingetz.datafy-git2)
            #?(:clj dustingetz.datafy-jvm2)
            #?(:clj dustingetz.datafy-fs)
            #?(:clj dustingetz.datafy-clj)
            [contrib.data :refer [map-entry]]
            #?(:clj [datomic.api :as d])
            [datomic-browser.datomic-browser :refer [Inject-datomic]]
            #?(:clj dustingetz.datomic-contrib) ; datafy entity
            [dustingetz.entity-browser2 :refer []]
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            #?(:clj [dustingetz.y2020.hfql.hfql11 :refer [hf-pull]])
            [dustingetz.treelister3 :as tl]))

#?(:clj (defn resolve-class [whiteset qs]
          (try (some-> (whiteset qs) resolve) (catch Exception e nil))))

#_
(e/defn UserResolve [[tag id]]
  (e/server
    (case tag
      :tap (Tap)
      :thread-mx (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread-manager))
      :thread (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread id))
      :git
      :class (ex/Offload-reset #(resolve-class #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean} id))
      :file
      'clojure.core/all-ns (ex/Offload-reset #(vec (sort-by ns-name (all-ns))))
      (e/amb))))

(e/defn GitRepo [repo-path]
  #_(ex/Offload-reset #(dustingetz.datafy-git2/load-repo repo-path))
  #_(EntityBrowser2 (e/server (map-entry uri (UserResolve uri)))))

(e/defn File [file-path]
  #_(ex/Offload-reset #(clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path)))
  #_(EntityBrowser2 (e/server (map-entry uri (UserResolve uri)))))

#_
(def targets [[['clojure.core/all-ns] ['clojure.core]] [[:git]] [[:file "./"]] [[:thread-mx]] [[:tap]]
              [[:class 'org.eclipse.jgit.api.Git]]
              [[:class 'java.lang.management.ThreadMXBean]]])

(e/defn Index [_sitemap]
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [`GitRepo "./"]] (dom/text "GitRepo"))
    (r/link ['. [`File "./"]] (dom/text "File"))))

#_(e/for [uri (e/diff-by identity (e/as-vec uri))]) ; workaround glitch (Leo, please explain?)

#?(:clj (def !sitemap
          {`GitRepo []
           `File []}))

(declare css)
(e/defn Fiddles []
  {`ObjectBrowserDemo2
   (e/fn []
     (binding [pages {`GitRepo GitRepo
                      `File File}]
       (dom/style (dom/text css tooltip/css))
       (let [sitemap (e/server (e/watch !sitemap))]
         (Index sitemap)
         (TooltipArea (e/fn []
                        (Tooltip)
                        (HfqlRoot sitemap :default `(GitRepo)))))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser .-datomic-browser-dbob-db-stats table { grid-template-columns: 36ch auto;}")
