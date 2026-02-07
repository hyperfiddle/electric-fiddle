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

#?(:clj (defn http-req [] e/http-request))

#?(:clj
   (def sitemap
     {'file
      (hfql {(io/file ".")
             [java.io.File/.getName
              {java.io.File/.listFiles {* ...}}
              type]})

      'http-req (hfql {(http-req) [*]})

      'load-repo
      (hfql {(git/load-repo nav-git/git-repo-path)
             [.getRepository
              git/git-branch-current
              git/git-status
              {nav-git/branch-list {* [* nav-git/ref-log]}}
              nav-git/log]})

      'py-hello-world
      (hfql {(dustingetz.nav-py/py-hello-world) []})

      'py-environ
      (hfql {(dustingetz.nav-py/py-environ) []})

      'py-os-dir
      (hfql {(dustingetz.nav-py/py-os)
             [dustingetz.nav-py/dir]})

      'py-os-cpu-count
      (hfql {(dustingetz.nav-py/py-os)
             [dustingetz.nav-py/cpu-count]})

      'py-platform
      (hfql {(dustingetz.nav-py/py-platform)
             [dustingetz.nav-py/dir]})

      'jvm
      (hfql {(nav-jvm/getAllThreads (nav-jvm/getThreadMXBean))
             {*
              [.getThreadId
               .getThreadName]}})

      'list-project-jars
      (hfql {(dustingetz.nav-jar/list-project-jars)
             {* [dustingetz.nav-jar/jar-filename
                 dustingetz.nav-jar/jar-manifest
                 .getName
                 .getVersion
                 dustingetz.nav-jar/jar-entries
                 type]}})

      'deps
      (hfql {(deps/slurp-deps (fs/maybe-file "deps.edn")) []})

      'mvn
      (hfql {(mvn/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}])
             [.getUrl .getContentType .getAuthentication identity type]})

      'kondo
      (hfql {(kondo/run! {:lint ["src"] :config {:analysis true}})
             [*
              {:analysis [* (fn namespace-usages [m] ; FIXME extra `namespace-usages` column is lost due to the UI navigator reshaping this step as a key×value list.
                              (->> (get m :namespace-usages)
                                (map #(hfql/identifiable (juxt :from :to) %))))]}]})

      'getMemoryMXBean
      (hfql {(nav-jvm/getMemoryMXBean)
             [.getHeapMemoryUsage
              .getNonHeapMemoryUsage
              .getObjectPendingFinalizationCount]})

      'getOperatingSystemMXBean
      (hfql {(nav-jvm/getOperatingSystemMXBean)
             [.getArch .getAvailableProcessors .getCpuLoad .getSystemCpuLoad type]})

      'getRuntimeMXBean
      (hfql {(nav-jvm/getRuntimeMXBean)
             [.getLibraryPath .getPid .getSystemProperties .getUptime type]})

      'getThreadMXBean
      (hfql {(nav-jvm/getThreadMXBean)
             [{nav-jvm/getAllThreads {* [type
                                         .getLockInfo
                                         .getThreadName
                                         .getThreadState
                                         .getWaitedCount
                                         .getWaitedTime
                                         .getThreadId]}}
              .findDeadlockedThreads
              type]})

      'aws
      (hfql {(nav-aws/aws-s3-us-east)
             [nav-aws/list-buckets
              nav-aws/aws-ops
              type]})

      'ns
      (hfql {(find-ns 'clojure.core #_(ns-name *ns*))
             [dustingetz.nav-clojure-ns/ns-publics2]})}))

(e/defn NavigatorDemo1 [sitemap entrypoints]
  (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/electric-forms.css"}))
  (dom/link (dom/props {:rel :stylesheet :href "/hyperfiddle/datomic-browser2.css"}))
  (Checkbox* false {:class "data-loader__enabled" :style {:position :absolute,
                                                          :inset-block-start "1dvw",
                                                          :inset-inline-end "1dvw"}})
  (e/server
    (binding [e/*bindings* {#'e/http-request e/http-request
                            #'nav-git/*repo-path* nav-git/git-repo-path}] ; DI
      (HfqlRoot sitemap entrypoints))))

;; Identifiable, Suggestable

#?(:clj (extend-type java.io.File
          hfqlp/Identifiable (-identify [^java.io.File o] `(clojure.java.io/file ~(.getPath o)))
          hfqlp/Suggestable (-suggest [o] (hfql [java.io.File/.getName
                                                 java.io.File/.getPath
                                                 java.io.File/.getAbsolutePath
                                                 {java.io.File/.listFiles {* ...}}]))))

#?(:clj (defmethod hfqlp/-hfql-resolve `clojure.java.io/file [[_ file-path-str]] (clojure.java.io/file file-path-str)))

#?(:clj (extend-type clojure.lang.Namespace
          hfqlp/Identifiable (-identify [ns] `(find-ns ~(ns-name ns)))
          hfqlp/Suggestable (-suggest [_] (hfql [ns-name ns-publics meta]))))

#?(:clj (extend-type clojure.lang.Var
          hfqlp/Identifiable (-identify [ns] `(find-var ~(symbol ns)))
          hfqlp/Suggestable (-suggest [_] (hfql [symbol meta .isMacro .isDynamic .getTag]))))
