(ns dustingetz.navigator-demo1
  (:require
    #?(:clj [dustingetz.fs2 :as fs])
    #?(:clj [dustingetz.nav-clojure-ns])
    #?(:clj [clj-jgit.porcelain :as git])
    #?(:clj [clj-kondo.core :as kondo])
    #?(:clj [clojure.tools.deps :as deps])
    #?(:clj [clojure.tools.deps.util.maven :as mvn])
    #?(:clj [dustingetz.nav-jvm :as nav-jvm])
    #?(:clj dustingetz.nav-jar)
    #?(:clj dustingetz.nav-py)
    #?(:clj [dustingetz.nav-aws :as nav-aws])
    #?(:clj [dustingetz.nav-git :as nav-git])
    #?(:clj [clojure.java.io :as io])
    [hyperfiddle.electric-dom3 :as dom]
    [hyperfiddle.electric-forms5 :refer [Checkbox*]]
    [hyperfiddle.electric3 :as e]
    [hyperfiddle.hfql2 :as hfql :refer [hfql]]
    [hyperfiddle.hfql2.protocols :as hfqlp]
    [hyperfiddle.navigator6 :as navigator :refer [HfqlRoot]]))

#?(:clj
   (def datomic-browser-sitemap
     {'file
      (hfql {(io/file ".")
             [java.io.File/.getName
              {java.io.File/.listFiles {* ...}}
              type]})

      'load-repo
      (hfql {(git/load-repo "../")
             [.getRepository
              git/git-branch-current
              git/git-status
              nav-git/branch-list
              nav-git/log]})

      'py-hello-world
      (hfql []
            ;; TODO: Why not `:directory-contents`?
            (dissoc (dustingetz.nav-py/py-hello-world) :dustingetz.nav-py/directory-contents))

      'py-environ
      (hfql {(dustingetz.nav-py/py-environ)
             []})

      'py-os-dir
      (hfql {(dustingetz.nav-py/py-os)
             [dustingetz.nav-py/dir]})

      'py-os-cpu-count
      (hfql {(dustingetz.nav-py/py-os)
             [dustingetz.nav-py/cpu-count]})

      'py-platform
      (hfql [dustingetz.nav-py/dir] (dustingetz.nav-py/py-platform))

      'jvm
      (hfql {*
             [.getThreadId
              .getThreadName]}
            (nav-jvm/getAllThreads (nav-jvm/getThreadMXBean)))

      'list-project-jars
      (hfql {(dustingetz.nav-jar/list-project-jars)
             {* [dustingetz.nav-jar/jar-filename
                 dustingetz.nav-jar/jar-manifest
                 .getName
                 .getVersion
                 dustingetz.nav-jar/jar-entries
                 type]}})

      'deps
      (hfql []
        (deps/slurp-deps (fs/maybe-file "deps.edn")))

      'mvn
      (hfql [.getUrl .getContentType .getAuthentication identity type]
        (mvn/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}]))

      'kondo
      (hfql [{:analysis [* (fn namespace-usages [m]
                             (map #(hfql/identifiable (juxt :from :to) %)  (get m :namespace-usages)))]}]
        (kondo/run! {:lint ["src"]
                     :config {:analysis true}}))

      'getMemoryMXBean
      (hfql [.getHeapMemoryUsage
             .getNonHeapMemoryUsage
             .getObjectPendingFinalizationCount]
        (nav-jvm/getMemoryMXBean))

      'getOperatingSystemMXBean
      (hfql [.getArch .getAvailableProcessors .getCpuLoad .getSystemCpuLoad
             type]
        (nav-jvm/getOperatingSystemMXBean))

      'getRuntimeMXBean
      (hfql [.getLibraryPath .getPid .getSystemProperties .getUptime type]
        (nav-jvm/getRuntimeMXBean))

      'getThreadMXBean
      (hfql [{nav-jvm/getAllThreads {* [type
                                        .getLockInfo
                                        .getThreadName
                                        .getThreadState
                                        .getWaitedCount
                                        .getWaitedTime
                                        .getThreadId]}}
             .findDeadlockedThreads
             type]
        (nav-jvm/getThreadMXBean))

      'aws
      (hfql [nav-aws/list-buckets
             nav-aws/aws-ops
             type]
        (nav-aws/aws-s3-us-east))

      'ns
      (hfql [dustingetz.nav-clojure-ns/ns-publics2]
        *ns*)}))

(e/defn NavigatorDemo1 [sitemap entrypoints]
  (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/electric-forms.css"}))
  (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/datomic-browser2.css"}))
  (Checkbox* false {:class "data-loader__enabled" :style {:position :absolute,
                                                          :inset-block-start "1dvw",
                                                          :inset-inline-end "1dvw"}})
  (e/server (HfqlRoot sitemap entrypoints)))

;; Identifiable, Suggestable

#?(:clj (extend-type java.io.File
          hfqlp/Identifiable (identify [^java.io.File o] `(clojure.java.io/file ~(.getPath o)))
          hfqlp/Suggestable (suggest [o] (hfql [java.io.File/.getName
                                                java.io.File/.getPath
                                                java.io.File/.getAbsolutePath
                                                {java.io.File/.listFiles {* ...}}]))))

#?(:clj (extend-type clojure.lang.Namespace
          hfqlp/Identifiable (identify [ns] `(find-ns ~(ns-name ns)))
          hfqlp/Suggestable (suggest [_] (hfql [ns-name ns-publics meta]))))

#?(:clj (extend-type clojure.lang.Var
          hfqlp/Identifiable (identify [ns] `(find-var ~(symbol ns)))
          hfqlp/Suggestable (suggest [_] (hfql [symbol meta .isMacro .isDynamic .getTag]))))
