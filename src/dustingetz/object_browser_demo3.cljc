(ns dustingetz.object-browser-demo3
  #?(:clj (:import org.eclipse.jgit.api.Git
                   [java.lang.management ManagementFactory]
                   [java.lang.management ThreadInfo]
                   [com.sun.management ThreadMXBean]))
  (:require [clojure.string :as str]
            [contrib.debug :as dbg]
            [contrib.assert :as ca]
            #?(:clj [datomic.api :as d])
            [hyperfiddle.nav0 :refer [identify]]
            [dustingetz.entity-browser4 :as eb]
            #?(:clj [dustingetz.datafy-git2 :as git])
            #?(:clj [dustingetz.datafy-jvm2])
            #?(:clj [clojure.java.io])
            #?(:clj [dustingetz.datafy-fs :as fs])
            #?(:clj dustingetz.datafy-clj)
            #?(:clj dustingetz.mbrainz)
            ;; #?(:clj dustingetz.pg-sakila)
            [clojure.core.protocols :as ccp]
            [dustingetz.str :as strx]
            [clojure.walk]
            #?(:clj [peternagy.hfql :as hfql])
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]))

(e/defn GitRepo [repo-path]
  (e/server (dustingetz.datafy-git2/load-repo repo-path)))

(e/defn File [file-path]
  (e/server (clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path))))

;;;;;;;;
;; NS ;;
;;;;;;;;

#?(:clj (defn clojure-all-ns [] (vec (sort-by ns-name (all-ns)))))

#?(:clj (defn ns-doc [ns] (-> ns meta :doc)))
#?(:clj (defn ns-publics* [_] '...))
#?(:clj (defn public-vars [ns$] (->> ns$ find-ns ns-publics vals (sort-by symbol))))
#?(:clj (defn var-detail [var$] (resolve var$)))

(comment
  (public-vars 'clojure.core)
  (public-vars (find-ns 'clojure.core)))

(e/defn Clojure-ns-vars [sym] (e/server (public-vars sym)))
#?(:clj (defn var-name [vr] (-> vr symbol name symbol)))
#?(:clj (defn var-doc [vr] (-> vr meta :doc)))
#?(:clj (defn var-macro? [vr] (.isMacro ^clojure.lang.Var vr)))
#?(:clj (defn var-arglists [vr] (->> vr meta :arglists (str/join " ") symbol))) ; lol

;;;;;;;;;;;;;
;; THREADS ;;
;;;;;;;;;;;;;

#?(:clj (defn thread-mx [] (ManagementFactory/getThreadMXBean)))
#?(:clj (defn thread-count [^ThreadMXBean tmxb] (.getThreadCount tmxb)))
#?(:clj (defn get-all-threads [_] '...))
#?(:clj (defn threads-info []
          (let [tmxb (thread-mx)]
            (with-meta (vec (.getAllThreadIds (thread-mx)))
              {`ccp/nav (fn [_this _k v] (.getThreadInfo tmxb v))}))))
#?(:clj (defn thread-info [id]
          (let [tmxb (thread-mx)]
            (.getThreadInfo tmxb id))))
#?(:clj (defn get-stack-trace [_] '...))
#?(:clj (defn thread-stack-trace
          [id] (let [tmxb (thread-mx)]
                 (vec (.getStackTrace (.getThreadInfo tmxb id Integer/MAX_VALUE))))))
#?(:clj (defn thread-cpu-time [^ThreadInfo x]
          (.getThreadCpuTime (ManagementFactory/getThreadMXBean) x)))
#?(:clj (defn get-deadlocked-threads [^ThreadMXBean tmxb]
          (vec (.findDeadlockedThreads tmxb))))

#?(:clj (defn datomic-entity [e] (d/entity @dustingetz.mbrainz/test-db e)))

#?(:clj (defn class-view [class$]
          (dustingetz.datafy-jvm2/resolve-class
            #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean}
            class$)))

(e/defn Class_ [class-name]
  (e/server (dustingetz.datafy-jvm2/resolve-class
              #{'org.eclipse.jgit.api.Git 'java.lang.management.ThreadMXBean}
              class-name)))

(e/defn Thread_ [thread-id]
  (e/server (dustingetz.datafy-jvm2/resolve-thread thread-id)))

(e/defn DatomicEntity [e]
  (e/server (d/entity @dustingetz.mbrainz/test-db e)))

;; #?(:clj (defn sakila [] (dustingetz.pg-sakila/query-films @dustingetz.pg-sakila/test-conn)))

(e/defn Edn [x] x)

(e/defn Tap [])

#?(:clj (defn file-exists? [path] (.exists (clojure.java.io/file path))))
#?(:clj (def git-repo-path (first (filter file-exists? ["./.git" "../.git"]))))

(e/defn Index [_sitemap]
  (e/client
    (dom/props {:class "Index"})
    (dom/text "Nav: ")
    (r/link ['. [[`clojure-all-ns]]] (dom/text "clojure.core"))
    (r/link ['. [[`dustingetz.datafy-git2/load-repo git-repo-path]]] (dom/text "git"))
    (r/link ['. [['clojure.java.io/file "./"]]] (dom/text "file"))
    (r/link ['. [[`thread-mx]]] (dom/text "thread-mx"))
    ;; (r/link ['. [[`sakila]]] (dom/text "Sakila"))
    (r/link ['. [[`datomic-entity dustingetz.mbrainz/lennon]]] (dom/text "datomic"))
    #_(r/link ['. [[`Thread_ 0]]] (dom/text "Thread 0"))
    (r/link ['. [[`class-view 'java.lang.management.ThreadMXBean]]] (dom/text "class"))))

#?(:clj (def sitemap-path "dustingetz/object_browser_demo3.edn"))
#?(:clj (def sitemap (eb/read-sitemap sitemap-path *ns*)))
#?(:clj (defn sitemap-writer [file-path] (fn [v] (spit file-path (strx/pprint-str v)))))

(declare css)

#?(:clj (defn route-ns [o] (when (instance? clojure.lang.Namespace o) (list `find-ns (ns-name o))))) ; page affinity

(e/defn ObjectBrowserDemo3 []
  (binding [eb/*hfql-bindings (e/server {})
            eb/*sitemap (e/server sitemap)
            eb/*sitemap-writer (e/server (sitemap-writer sitemap-path))
            eb/*page-defaults (e/server [route-ns])
            #_#_eb/Timing dustingetz.offload-ui/OffloadUI] ; enable long-running queries monitoring
    (dom/style (dom/text css))
    (let [sitemap eb/*sitemap]
      (dom/style (dom/text css))
      (Index sitemap)
      (eb/HfqlRoot sitemap `[(clojure-all-ns)]))))


(def css "
html { scrollbar-gutter: stable; } /* prevent layout jump when scrollbar shows/hide */
.Index > a+a { margin-left: .5em; }
.Browser fieldset.entity table { grid-template-columns: 20em auto; }

/* Explicit table height - there are alternatives */
.Browser fieldset.dustingetz-entity-browser4__block table { height: calc(16 * var(--row-height)); } /* 15 rows + header row */
.Browser fieldset.dustingetz-entity-browser4__block { height: fit-content; }


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
