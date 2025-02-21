(ns dustingetz.object-browser-demo2
  #?(:clj (:import org.eclipse.jgit.api.Git))
  (:require [contrib.data :refer [map-entry]]
            [contrib.debug :as dbg]
            [clojure.string :as str]
            #?(:clj [datomic.api :as d])
            [dustingetz.identify :refer [identify]]
            [dustingetz.entity-browser3 :refer [HfqlRoot *hfql-spec TableBlock TreeBlock]]
            #?(:clj [dustingetz.datafy-git2 :as git])
            #?(:clj dustingetz.datafy-jvm2)
            #?(:clj [dustingetz.datafy-fs :as fs])
            #?(:clj dustingetz.datafy-clj)
            #?(:clj dustingetz.mbrainz)
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric3-contrib :as ex]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.ui.tooltip :as tooltip :refer [TooltipArea Tooltip]]))

(e/defn GitRepo [repo-path]
  (e/server (dustingetz.datafy-git2/load-repo repo-path)))

(e/defn File [file-path]
  (e/server (clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path))))

(e/defn Clojure-all-ns []
  (e/server (vec (sort-by ns-name (all-ns)))))

(e/defn Clojure-ns-detail [x] (e/server (find-ns x)))
#?(:clj (defn ns-doc [ns] (-> ns meta :doc)))
#?(:clj (defn public-vars [?ns] (when ?ns (->> ?ns #_find-ns ns-publics vals (sort-by symbol)))))
#?(:clj (defn public-vars* [ns] (identify ns))) ; HACK this value fills the link, TODO hfql scopes conveyance into walker, TreeBlock and TableBlock

(comment
  (public-vars 'clojure.core)
  (public-vars (find-ns 'clojure.core)))

(e/defn Clojure-ns-vars [sym] (e/server (public-vars sym)))
#?(:clj (defn var-name [vr] (-> vr symbol name symbol)))
#?(:clj (defn var-doc [vr] (-> vr meta :doc)))
#?(:clj (defn var-macro? [vr] (.isMacro ^clojure.lang.Var vr)))
#?(:clj (defn var-arglists [vr] (->> vr meta :arglists (str/join " ") symbol))) ; lol

(e/defn Clojure-var-detail [sym] (e/server (->> sym resolve)))

(e/defn ThreadMX []
  (e/server (dustingetz.datafy-jvm2/resolve-thread-manager)))

(e/defn Class_ [class-name]
  (e/server (dustingetz.datafy-jvm2/resolve-class
              #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean}
              class-name)))

(e/defn Thread_ [thread-id]
  (e/server (dustingetz.datafy-jvm2/resolve-thread thread-id)))

(e/defn DatomicEntity [e]
  (e/server (d/entity @dustingetz.mbrainz/test-db e)))

(e/defn Tap [])

(e/defn Index [_sitemap]
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [[`Clojure-all-ns]]] (dom/text "clojure.core"))
    (r/link ['. [[`GitRepo "./"]]] (dom/text "git"))
    (r/link ['. [[`File "./"]]] (dom/text "file"))
    (r/link ['. [[`ThreadMX]]] (dom/text "thread-mx"))
    (r/link ['. [[`DatomicEntity dustingetz.mbrainz/lennon]]] (dom/text "datomic"))
    #_(r/link ['. [[`Thread_ 0]]] (dom/text "Thread 0"))
    (r/link ['. [[`Class_ 'java.lang.management.ThreadMXBean]]] (dom/text "class"))
    (r/link ['. [[`Tap]]] (dom/text "Tap"))))

#?(:clj (def !sitemap
          (atom
            {`GitRepo [#_'*
                       `(git/repo-repo ~'%)
                       `(git/status ~'%)
                       `(git/branch-current ~'%)
                       `(git/branch-list ~'%)
                       `(git/log ~'%) ; navigable; :log is not
                       #_{`(git/log ~'%)
                        [`(git/commit-short-name ~'%)]}]
             `File ['*
                    #_#_#_#_#_#_
                    `(fs/file-name ~'%)
                    `(fs/file-hidden? ~'%)
                    `(fs/file-name ~'%)
                    `(fs/file-kind ~'%)
                    `(fs/file-created ~'%)
                    `(fs/dir-list ~'%)
                    #_`(fs/dir-list ~'%) #_(with-meta {:hf/Render .} `(fs/dir-list ~'%))]
             `Clojure-all-ns (with-meta [`(ns-name ~'%) `(ns-doc ~'%)]
                               {:hf/select `(Clojure-ns-detail ~'%)})
             `Clojure-ns-detail [`(ns-name ~'%) `(ns-doc ~'%) `(meta ~'%)
                                 #_`(ns-publics ~'%)
                                 (with-meta `(public-vars* ~'%) {:hf/select `(Clojure-ns-vars ~'%)})
                                 `(ns-imports ~'%)
                                 `(ns-interns ~'%)]
             `Clojure-ns-vars (with-meta [`(var-name ~'%) `(var-doc ~'%)]
                                {:hf/select `(Clojure-var-detail ~'%)})
             `Clojure-var-detail [`(var-name ~'%) `(var-doc ~'%)
                                  `(meta ~'%)
                                  `(var-macro? ~'%) `(var-arglists ~'%)]
             `DatomicEntity ['*]
             `ThreadMX ['*]
             `Thread_ ['*]
             `Class_ ['*]
             `Tap ['*]
             #_#_`Entity ['{* [*]}]})))

(declare css)

(e/defn ObjectBrowserDemo2 []
  (binding [pages {`Clojure-all-ns Clojure-all-ns
                   `Clojure-ns-detail Clojure-ns-detail
                   `Clojure-ns-vars Clojure-ns-vars
                   `Clojure-var-detail Clojure-var-detail
                   `GitRepo GitRepo
                   `File File
                   `DatomicEntity DatomicEntity
                   `ThreadMX ThreadMX
                   `Class_ Class_
                   `Thread_ Thread_
                   `Tap Tap}]
    (dom/style (dom/text css))
    (let [sitemap (e/server (e/watch !sitemap))]
      (Index sitemap)
      (HfqlRoot sitemap :default `[(Clojure-all-ns)]))))


(def css "
.Index > a+a { margin-left: .5em; }
.Browser.dustingetz-EasyTable { position: relative; } /* re-hack easy-table.css hack */
.Browser fieldset.entity table { grid-template-columns: 20em auto; }
")

(comment
  (require '[clojure.datafy :refer [datafy nav]]
    '[dustingetz.hfql11 :refer [hf-pull hf-pull2 hf-pull3]])
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
  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) 2}]
    (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./src")))

  (hf-pull3 [`(fs/file-name ~'%) {`(fs/dir-list ~'%) '...}]
    (clojure.java.io/file (dustingetz.datafy-fs/absolute-path "./src")))

  nil ; don't print tons of files at the REPL
  )
