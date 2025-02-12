(ns dustingetz.object-browser-demo2
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [contrib.data :refer [map-entry]]
            [dustingetz.entity-browser1 :refer [HfqlRoot *hfql-spec]]
            [dustingetz.entity-browser2 :refer
             [TableBlock TreeBlock TreeBlock2 Render
              EntityBrowser2]]
            #?(:clj [dustingetz.datafy-git2 :as git])
            #?(:clj dustingetz.datafy-jvm2)
            #?(:clj [dustingetz.datafy-fs :as fs])
            #?(:clj dustingetz.datafy-clj)
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex :refer [Tap]]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            #?(:clj [dustingetz.y2020.hfql.hfql11 :refer [hf-pull]])
            #?(:clj [datomic-browser.dbob :refer [treelist]]) ; fixme
            ))

#?(:clj (defn resolve-class [whiteset qs]
          (try (some-> (whiteset qs) resolve) (catch Exception e nil))))

#_
(e/defn UserResolve [[tag id]]
  (e/server
    (case tag
      :tap (Tap)
      :thread-mx (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread-manager))
      :thread (ex/Offload-reset #(dustingetz.datafy-jvm2/resolve-thread id))
      :class (ex/Offload-reset #(resolve-class #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean} id))
      (e/amb))))

(e/defn GitRepo [repo-path]
  #_(ex/Offload-reset #(dustingetz.datafy-git2/load-repo repo-path))
  #_(EntityBrowser2 (e/server (map-entry uri (UserResolve uri))))
  (e/client
    (TreeBlock ::select-git
      (e/server (map-entry `GitRepo (dustingetz.datafy-git2/load-repo repo-path)))
      nil
      :cols *hfql-spec)))

(e/defn File [file-path]
  (e/client
    (TreeBlock ::select
      (e/server (map-entry `File (clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path))))
      nil
      #_(e/server (fn children-fn [search x] []
                  #_(treelist #(get % `(fs/dir-list ~'%))
                                             #(contrib.str/includes-str? % search) x)))
      :cols *hfql-spec)))

(e/defn Clojure-all-ns []
  (e/client
    (TableBlock ::select
      (e/server (map-entry `Clojure-all-ns (fn [search] (vec (sort-by ns-name (all-ns)))) #_*hfql-spec))
      nil *hfql-spec)))

(comment

  (require '[clojure.datafy :refer [datafy nav]])

  (clojure.datafy/datafy *ns*)
  ((hf-pull ['*]) {'% *ns*})
  ((hf-pull [:name :publics :imports :interns]) {'% (datafy *ns*)})
  ((hf-pull [`(ns-name ~'%) `(ns-publics ~'%) `(ns-imports ~'%) `(ns-interns ~'%)]) {'% *ns*})

  (ns-name *ns*)

  (datafy (class *ns*))

  (def x (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./")))
  ((hf-pull ['*]) {'% x})
  ((hf-pull '*) {'% x})
  ((hf-pull [`(fs/dir-list ~'%) `(fs/file-name ~'%)]) {'% x})
  ((hf-pull [#_`(fs/dir-list ~'%) `(fs/file-name ~'%)]) {'% x})
  ((hf-pull `(fs/file-name ~'%)) {'% x})

  )

#_
(def targets [[[:thread-mx]] [[:tap]]
              [[:class 'org.eclipse.jgit.api.Git]]
              [[:class 'java.lang.management.ThreadMXBean]]])

(e/defn Index [_sitemap]
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [`Clojure-all-ns]] (dom/text "clojure.core"))
    (r/link ['. [`GitRepo "./"]] (dom/text "GitRepo"))
    (r/link ['. [`File "./"]] (dom/text "File"))))

#_(e/for [uri (e/diff-by identity (e/as-vec uri))]) ; workaround glitch (Leo, please explain?)

#?(:clj (def !sitemap
          (atom
            {`GitRepo [`(git/repo-repo ~'%)
                       `(git/status ~'%)
                       `(git/branch-current ~'%)
                       `(git/branch-list ~'%)
                       `(git/log ~'%)]
             `File [`(fs/file-name ~'%)
                    `(fs/file-hidden? ~'%)
                    `(fs/file-name ~'%)
                    `(fs/file-kind ~'%)
                    `(fs/file-created ~'%)
                    `(fs/dir-list ~'%) #_(with-meta {:hf/Render .} `(fs/dir-list ~'%))
                    #_{`(fs/dir-list ~'%) ['*]}]
             `Clojure-all-ns [`(ns-name ~'%) `(ns-publics ~'%) `(ns-imports ~'%) `(ns-interns ~'%)]})))

(declare css)
(e/defn Fiddles []
  {`ObjectBrowserDemo2
   (e/fn []
     (binding [pages {`GitRepo GitRepo
                      `File File
                      `Clojure-all-ns Clojure-all-ns}]
       (dom/style (dom/text css))
       (let [sitemap (e/server (e/watch !sitemap))]
         (Index sitemap)
         (HfqlRoot sitemap :default `(Clojure-all-ns)))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser .-datomic-browser-dbob-db-stats table { grid-template-columns: 36ch auto;}")
