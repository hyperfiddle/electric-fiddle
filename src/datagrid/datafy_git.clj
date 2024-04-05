(ns datagrid.datafy-git
  (:require [clj-jgit.porcelain :as git]
            [clj-jgit.querying :as git2]
            [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]]))

(extend-protocol ccp/Datafiable
  org.eclipse.jgit.api.Git
  (datafy [^org.eclipse.jgit.api.Git repo]
    (-> {:status (git/git-status repo)
         :log `...}
      (with-meta
        {`ccp/nav (fn [x k v]
                    (case k
                      :log
                      (let [rev-walk (clj-jgit.internal/new-rev-walk repo)
                            index    (git2/build-commit-map repo rev-walk)] ; pre-compute for fast ref resolve
                        (map (fn [commit] (with-meta commit {`ccp/datafy (fn datafy [commit] (git2/commit-info repo rev-walk index (:id commit)))}))
                          (git/git-log repo)))
                      v))}))))

(defn short-commit-id [id] (apply str (take 7 id)))

(defn get-commit
  ([repo commit-id] (get-commit repo (clj-jgit.internal/new-rev-walk repo) commit-id))
  ([repo rev-walk commit-id] (get-commit repo rev-walk (git2/build-commit-map repo rev-walk) commit-id))
  ([repo rev-walk commit-map commit-id]
   (clj-jgit.querying/commit-info
     repo
     rev-walk
     commit-map
     (clj-jgit.querying/find-rev-commit repo
       rev-walk
       commit-id))))

(comment
  (def r (git/load-repo "./"))
  (git/git-status r)
  (type r)
  (datafy r)
  (nav (datafy r) :log ())
  (count (git/git-log r))
  (type (git/git-log r))
  (def x (first (git/git-log r)))
  (type x)
  x
  (keys x)
  (def c (:id x))
  (type c)
  (datafy c)
  (def ci (git2/commit-info r c))
  (prn ci)
  (type ci)
  (keys ci)
  (.getShortMessage c))
