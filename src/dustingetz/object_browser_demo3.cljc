(ns dustingetz.object-browser-demo3
  #?(:clj (:import org.eclipse.jgit.api.Git
                   [java.lang.management ManagementFactory]
                   [java.lang.management ThreadInfo]
                   [com.sun.management ThreadMXBean]))
  (:require [clojure.string :as str]
            [contrib.debug :as dbg]
            [contrib.assert :as ca]
            #?(:clj [datomic.api :as d])
            [hyperfiddle.navigator4 :as navigator]
            #?(:clj [dustingetz.datafy-jvm2])
            #?(:clj [clojure.java.io])
            #?(:clj [dustingetz.datafy-fs :as fs])
            #?(:clj dustingetz.mbrainz)
            #?(:clj dustingetz.pg-sakila)
            [clojure.core.protocols :as ccp]
            [dustingetz.str :as strx]
            [clojure.walk]
            #?(:clj [hyperfiddle.hfql0 :as hfql])
            [electric-fiddle.fiddle-index :refer [pages]]
            [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router5 :as r]
            #_[dustingetz.loader]
            #?(:clj [clojure.tools.logging :as log])))

(e/defn File [file-path]
  (e/server (clojure.java.io/file (dustingetz.datafy-fs/absolute-path file-path))))

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

#?(:clj (defn sakila [] (dustingetz.pg-sakila/query-films @dustingetz.pg-sakila/test-conn)))

(e/defn Edn [x] x)

(e/defn Tap [])

#?(:clj (def sitemap-path "dustingetz/object_browser_demo3.edn"))
#?(:clj (def sitemap (hfql/read-sitemap (ns-name *ns*) sitemap-path)))
#?(:clj (defn sitemap-writer [file-path] (fn [v] (spit file-path (strx/pprint-str v)))))

(declare css)

(e/defn ObjectBrowserDemo3 []
  (binding [e/*bindings* {}
            navigator/*sitemap-writer (e/server (sitemap-writer sitemap-path))
            #_#_navigator/*page-defaults (e/server [])
            navigator/*server-pretty (e/server (assoc navigator/*server-pretty datomic.query.EntityMap (fn [em] (str "datomic.query.EntityMap" (pr-str em)))))
            #_#_navigator/Timing (e/fn [label f] (dustingetz.loader/Offload f {:label label}))] ; enable long-running queries monitoring
    (dom/style (dom/text css))
    (navigator/HfqlRoot (e/server sitemap)
      `[(clojure.java.io/file ".")
        (datomic-entity ~dustingetz.mbrainz/lennon)])))


(def css "
html { scrollbar-gutter: stable; } /* prevent layout jump when scrollbar shows/hide */
.Index > a+a { margin-left: .5em; }
.Browser fieldset.entity table { grid-template-columns: 20em auto; }

/* Explicit table height - there are alternatives */
.Browser fieldset.hyperfiddle-navigator4__block table { height: calc(16 * var(--row-height)); } /* 15 rows + header row */
.Browser fieldset.hyperfiddle-navigator4__block { height: fit-content; }


")
