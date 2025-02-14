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
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]
            #?(:clj [datomic-browser.dbob :refer [treelist]]) ; fixme
            ))

(e/defn GitRepo [repo-path]
  (e/client
    (TreeBlock ::select-git
      (e/server (map-entry `GitRepo (dustingetz.datafy-git2/load-repo repo-path)))
      nil :cols *hfql-spec)))

(e/defn File [file-path]
  (e/client
    (TreeBlock ::select
      (e/server (map-entry `File (clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path))))
      nil :cols *hfql-spec)))

(e/defn Clojure-all-ns []
  (e/client
    (TableBlock ::select
      (e/server (map-entry `Clojure-all-ns (fn [search] (vec (sort-by ns-name (all-ns)))) #_*hfql-spec))
      nil *hfql-spec)))

(e/defn ThreadMX []
  (e/client
    (TreeBlock ::select
      (e/server (map-entry `ThreadMX (dustingetz.datafy-jvm2/resolve-thread-manager)))
      nil :cols *hfql-spec)))

(e/defn Class_ [class-name]
  (e/client
    (TreeBlock ::select-git
      (e/server (map-entry `Class_ (dustingetz.datafy-jvm2/resolve-class
                                    #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean}
                                    class-name)))
      nil :cols *hfql-spec)))

(e/defn Thread_ [thread-id]
  (e/client
    (TreeBlock ::select-git
      (e/server (map-entry `Thread_ (dustingetz.datafy-jvm2/resolve-thread thread-id)))
      nil :cols *hfql-spec)))

(e/defn Tap [])

(e/defn Index [_sitemap]
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [`Clojure-all-ns]] (dom/text "clojure.core"))
    (r/link ['. [`GitRepo "./"]] (dom/text "git"))
    (r/link ['. [`File "./"]] (dom/text "file"))
    (r/link ['. [`ThreadMX]] (dom/text "thread-mx"))
    #_(r/link ['. [`Thread_ 0]] (dom/text "Thread 0"))
    (r/link ['. [`Class_ 'java.lang.management.ThreadMXBean]] (dom/text "class"))
    (r/link ['. [`Tap]] (dom/text "Tap"))))

(comment
  (require '[clojure.datafy :refer [datafy nav]]
    '[dustingetz.y2020.hfql.hfql11 :refer [hf-pull hf-pull2 hf-pull3]])
  (datafy *ns*)

  ((hf-pull ['*]) {'% *ns*})
  (hf-pull3 ['*] *ns*)

  ((hf-pull [:name :publics :imports :interns]) {'% (datafy *ns*)})
  (hf-pull3 [:name :publics :imports :interns] (datafy *ns*))

  ((hf-pull [`(ns-name ~'%) `(ns-publics ~'%) `(ns-imports ~'%) `(ns-interns ~'%)]) {'% *ns*})
  (hf-pull3 [`(ns-name ~'%) `(ns-publics ~'%) `(ns-imports ~'%) `(ns-interns ~'%)] *ns*)

  (def x (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./")))
  ((hf-pull ['*]) {'% x})
  (hf-pull3 ['*] x)

  ((hf-pull '*) {'% x})
  (hf-pull3 '* x)

  ((hf-pull [`(fs/dir-list ~'%) `(fs/file-name ~'%)]) {'% x})
  (hf-pull3 [`(fs/dir-list ~'%) `(fs/file-name ~'%)] x)

  ((hf-pull [#_`(fs/dir-list ~'%) `(fs/file-name ~'%)]) {'% x})
  (hf-pull3 [#_`(fs/dir-list ~'%) `(fs/file-name ~'%)] x)

  ((hf-pull `(fs/file-name ~'%)) {'% x})
  (hf-pull3 `(fs/file-name ~'%) x)

  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%)
                                  [`(fs/file-name ~'%)]}] x)

  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%)
                                  [`(fs/file-name ~'%) {`(fs/dir-list ~'%)
                                                        [`(fs/file-name ~'%)]}]}] x)

  (hf-pull3 {`(fs/dir-list ~'%) 0} x) := nil ; should it be empty map?
  (hf-pull3 [{`(fs/dir-list ~'%) 0}] x) := {} ; should it be nil?

  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) 0}] x)
  := '{(dustingetz.datafy-fs/file-name %) "electric-fiddle"}

  ;; unstable test, sensible to filesystem
  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) 1}]
    (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./src")))
  := '{(dustingetz.datafy-fs/file-name %) "src",
       (dustingetz.datafy-fs/dir-list %)
       ({(dustingetz.datafy-fs/file-name %) "contrib"}
        {(dustingetz.datafy-fs/file-name %) "datagrid"}
        {(dustingetz.datafy-fs/file-name %) "datomic_browser"}
        {(dustingetz.datafy-fs/file-name %) "docs_site"}
        {(dustingetz.datafy-fs/file-name %) "dustingetz"}
        {(dustingetz.datafy-fs/file-name %) "electric_essay"}
        {(dustingetz.datafy-fs/file-name %) "electric_fiddle"}
        {(dustingetz.datafy-fs/file-name %) "electric_tutorial"}
        {(dustingetz.datafy-fs/file-name %) "hello_fiddle"}
        {(dustingetz.datafy-fs/file-name %) "peternagy"}
        {(dustingetz.datafy-fs/file-name %) "scratch"}
        {(dustingetz.datafy-fs/file-name %) "staffly"}
        {(dustingetz.datafy-fs/file-name %) ".DS_Store"})}

  ;; unstable test, sensible to filesystem
  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) 2}]
    (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./src")))

  ;; unstable test, sensible to filesystem
  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) 3}]
    (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./src")))

  nil ; don't print tons of files at the REPL

  )

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
             `Clojure-all-ns [:name :publics '*
                              `(ns-name ~'%) `(ns-publics ~'%) `(ns-imports ~'%) `(ns-interns ~'%)]
             `ThreadMX ['*]
             `Thread_ ['*]
             `Class_ ['*]
             `Tap ['*]})))

(declare css)
(e/defn Fiddles []
  {`ObjectBrowserDemo2
   (e/fn []
     (binding [pages {`Clojure-all-ns Clojure-all-ns
                      `GitRepo GitRepo
                      `File File
                      `ThreadMX ThreadMX
                      `Class_ Class_
                      `Thread_ Thread_
                      `Tap Tap}]
       (dom/style (dom/text css))
       (let [sitemap (e/server (e/watch !sitemap))]
         (Index sitemap)
         (HfqlRoot sitemap :default `(Clojure-all-ns)))))})

(def css "
.Index > a+a { margin-left: .5em; }
.ThreadDump3 > a + a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser .-datomic-browser-dbob-db-stats table { grid-template-columns: 36ch auto;}")
