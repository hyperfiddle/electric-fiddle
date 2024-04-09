(ns datagrid.datafy-git
  (:require [clj-jgit.porcelain :as git]
            [clj-jgit.querying :as git2]
            [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.diff DiffFormatter DiffEntry RawTextComparator)
           (org.eclipse.jgit.revwalk RevWalk RevCommit RevCommitList)))

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
  (.getShortMessage c)

  (git2/changed-files r c)
  (println (git2/changed-files-with-patch r c))
  (def parent-commit (.getParent c 0))
  (def entries )
  (println (git2/changed-files-between-commits r parent-commit c))
)


(defn diff-entries-between-commits
  "List of files changed between two RevCommit objects"
  [^Git repo ^RevCommit old-rev-commit ^RevCommit new-rev-commit]
  (let [df ^DiffFormatter (#'git2/diff-formatter-for-changes repo)]
    (.scan df old-rev-commit new-rev-commit)))

;; (def entries (git2/changed-files-between-commits r parent-commit c))
(def WHITESPACE-MODE {::default         RawTextComparator/DEFAULT
                      ::ignore          RawTextComparator/WS_IGNORE_ALL
                      ::ignore-change   RawTextComparator/WS_IGNORE_CHANGE
                      ::ignore-leading  RawTextComparator/WS_IGNORE_LEADING
                      ::ignore-trailing RawTextComparator/WS_IGNORE_TRAILING})

(defn byte-array-diff-formatter-for-changes
  ([^Git repo ^java.io.ByteArrayOutputStream out] (byte-array-diff-formatter-for-changes repo out ::default))
  ([^Git repo ^java.io.ByteArrayOutputStream out whitespace-mode]
   (doto
       (new DiffFormatter out)
     (.setRepository (.getRepository repo))
     (.setDiffComparator (WHITESPACE-MODE whitespace-mode RawTextComparator/DEFAULT)))))

(defn format-entry-patch
  ([repo entry] (format-entry-patch repo entry ::default))
  ([repo entry whitespace-mode]
   (let [out       (java.io.ByteArrayOutputStream.)
         formatter (byte-array-diff-formatter-for-changes repo out whitespace-mode)]
     (.format formatter entry)
     (.toString out "UTF-8"))))

;; (map #(format-entry-patch r %) entries)

(defn parent-commit
  ([commit] (parent-commit commit 0))
  ([commit distance] (.getParent commit distance)))

(defn diffs
  ([^Git repo ^RevCommit commit] (diffs repo (parent-commit commit) commit))
  ([^Git repo ^RevCommit parent ^RevCommit commit]
   (diffs repo parent commit ::default))
  ([^Git repo ^RevCommit parent ^RevCommit commit whitespace-mode]
   (map #(format-entry-patch repo % whitespace-mode) (diff-entries-between-commits repo parent commit))))

