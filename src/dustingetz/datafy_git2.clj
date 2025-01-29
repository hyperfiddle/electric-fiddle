(ns dustingetz.datafy-git2
  (:require [clj-jgit.porcelain :as git]
            [clojure.core.protocols :refer [nav]]
            [clojure.datafy :refer [datafy]]
            [dustingetz.datafy-fs :as fs])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.internal.storage.file FileRepository)
           (org.eclipse.jgit.revwalk RevCommit)
           (org.eclipse.jgit.lib Constants ObjectId ObjectIdRef Ref Repository)))

; re-export wrappers for convenience - one API not two
(def load-repo (memoize git/load-repo))
(def log git/git-log)
(def branch-list git/git-branch-list)

(defn short-commit-id [id] (apply str (take 7 id)))

#_(defn remote-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_REMOTES))
#_(defn local-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_HEADS))
(defn ref-type [^Ref ref] (if (.startsWith (.getName ref) "refs/remotes/") :remote :local))

(extend-protocol clojure.core.protocols/Datafiable
  Git
  (datafy [^Git o]
    (->
      {#_#_:status (git/git-status o) ; keep :log above the fold in blog3 demo
       :repo (.getRepository o)
       :branch-current (git/git-branch-current o)
       :branches (memoize #(vec (branch-list o :jgit? true :list-mode :all))) ; arraylist
       :log (memoize #(vec (log o :until "HEAD")))}
      (with-meta {`nav
                  (fn [xs k v]
                    (case k
                      (:branches :log) (if v (v))
                      v))})))

  FileRepository
  (datafy [^FileRepository o]
    {:dir (.getDirectory o)})

  Ref #_ObjectIdRef ; including ObjectIdRef$PeeledNonTag
  (datafy [^ObjectIdRef o]
    {#_#_:ref-name (.getName o) ; refs/remotes/origin/agent-network, refs/heads/wip/hot-md
     :ref-name-short (Repository/shortenRefName (.getName o))
     :commit (-> o .getObjectId .getName) ; "88a97ef20b1fe5392a6025eca4170c80ad3479ce"
     :commit-short (-> o .getObjectId .getName short-commit-id)
     #_#_:symbolic (-> o .isSymbolic)
     #_#_:peeled (-> o .isPeeled)
     :ref-type (-> o ref-type) ; #{:local :remote}
     :object-id (.getObjectId o)}) ; #object[org.eclipse.jgit.lib.ObjectId

  ObjectId
  (datafy [^ObjectId o]
    {:toString (.toString o)
     :name (.getName o)})

  RevCommit
  (datafy [^RevCommit o]
    {:name (.getName o)
     :commit-short-name (-> o .getName short-commit-id)}))

(defn repo-path [o] (-> o datafy :repo datafy :dir datafy ::fs/absolute-path))

(comment
  (as-> (load-repo "./") x
    (datafy x)
    (nav x :log (:log x)))

  (as-> (load-repo "./") x
    (datafy x)
    (nav x :branches (:branches x))
    (datafy x)
    (nav x 0 (nth x 0))
    (datafy x))
  )