(ns dustingetz.nav-deps
  (:require [clojure.tools.deps :as deps]
            [clojure.tools.deps.util.maven :as mvn]
            [contrib.assert :refer [check]]
            [dustingetz.fs2 :as fs]))

(defn deps-project
  ([] (deps-project "deps.edn"))
  ([deps-filepath] (deps/slurp-deps (fs/maybe-file deps-filepath))))

(comment
  (fs/absolute-path "./deps.edn")
  (def !deps (deps-project "../electric/deps.edn"))

  (deps/calc-basis (merge !deps {:mvn/repos mvn/standard-repos}))

  (def ex-svc (concurrent/new-executor 2))

  (expand-deps {'org.clojure/clojure {:mvn/version "1.9.0"}}
    nil nil {:mvn/repos mvn/standard-repos} ex-svc true)

  (print-tree
    (resolve-deps {:deps {'org.clojure/clojure {:mvn/version "1.8.0"}
                          'org.clojure/core.memoize {:mvn/version "0.5.8"}}
                   :mvn/repos mvn/standard-repos} nil))

  )

(defn mvn [] (mvn/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}]))

(comment
  (def rr (mvn/remote-repo ["sonatype-oss-public" {:url "https://oss.sonatype.org/content/groups/public/"}]))
  )