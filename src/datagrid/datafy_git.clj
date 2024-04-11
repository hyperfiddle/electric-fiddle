(ns datagrid.datafy-git
  (:require [clj-jgit.porcelain :as git]
            [clj-jgit.querying :as git2]
            [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.diff DiffFormatter DiffEntry RawTextComparator)
           (org.eclipse.jgit.revwalk RevWalk RevCommit RevCommitList)))

(defn get-branches [^Git repo]
  (into {}
    (map (fn [[ref commit]]
           [(.getName ref) commit])
      (git2/branch-list-with-heads repo))))

(defn find-branches [branches commit]
  (->> (select-keys branches (:branches commit))
    (filter (fn [[_ref ref-commit]] (= (:raw commit) ref-commit)))
    (map first)))

(declare changes-stats)

(defn with-datafy
  ([commit] (with-datafy (:repo commit) commit))
  ([repo commit] (with-datafy repo (get-branches repo) commit))
  ([_repo branches commit]
   (with-meta commit {`ccp/datafy (fn datafy [commit]
                                    (let [original (:clojure.datafy/obj (meta commit) commit)
                                          branches (find-branches branches original)]
                                      (assoc commit ::branches branches
                                        ::changes (changes-stats original))))})))

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
                            index    (git2/build-commit-map repo rev-walk) ; pre-compute for fast ref resolve
                            branches (get-branches repo)]
                        (map (fn [commit] (with-datafy repo branches (git2/commit-info repo rev-walk index (:id commit))))
                          (git/git-log repo)))
                      v))}))))

(defn short-commit-id [id] (apply str (take 7 id)))

(defn get-commit
  ([repo commit-id] (get-commit repo (clj-jgit.internal/new-rev-walk repo) commit-id))
  ([repo rev-walk commit-id] (get-commit repo rev-walk (git2/build-commit-map repo rev-walk) commit-id))
  ([repo rev-walk commit-map commit-id]
   (with-datafy repo
     (clj-jgit.querying/commit-info
       repo
       rev-walk
       commit-map
       (clj-jgit.querying/find-rev-commit repo
         rev-walk
         commit-id)))))

(defn get-branch [^Git repo branch-ref-name]
  (get-commit repo (clj-jgit.internal/resolve-object branch-ref-name repo)))

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
     [(.getOldPath entry) (.toString out "UTF-8")])))

(defn parent-commit
  ([commit] (parent-commit commit 0))
  ([commit distance] (.getParent commit distance)))

(defn diffs
  ([^Git repo ^RevCommit commit] (diffs repo (parent-commit commit) commit))
  ([^Git repo ^RevCommit parent ^RevCommit commit]
   (diffs repo parent commit ::default))
  ([^Git repo ^RevCommit parent ^RevCommit commit whitespace-mode]
   (into {} (map #(format-entry-patch repo % whitespace-mode) (diff-entries-between-commits repo parent commit)))))



(comment
  (def c (datafy (get-commit r "8cb3d91")))
  (diff-entries-between-commits r (parent-commit (:raw c)) (:raw c))
  (def entry (second (diff-entries-between-commits r (parent-commit (:raw c)) (:raw c))))
  (def formatter (DiffFormatter. org.eclipse.jgit.util.io.DisabledOutputStream/INSTANCE))
  (.setRepository formatter (.getRepository r))
  (.toFileHeader formatter entry)

  (.getHunks (.toFileHeader formatter entry))
  (first (.getHunks (.toFileHeader formatter entry)))
  (def edits (.toEditList (first (.getHunks (.toFileHeader formatter entry)))))
  (reduce (fn [[adds rets] edit]
            [(+ adds (- (.getLengthB edit) (.getLengthA edit)))
             (+ rets (- (.getLengthA edit) (.getLengthB edit)))])
    [0 0] edits)

  )

(defn hunks [formatter entry] (.getHunks (.toFileHeader formatter entry)))

(defn edits [hunk]
  (reduce (fn [[adds rets] edit]
            [(+ adds (.getLengthB edit) #_(- (.getLengthB edit) #_(.getLengthA edit)))
             (+ rets (.getLengthA edit) #_(- (.getLengthA edit) #_(.getLengthB edit)))])
    [0 0] (.toEditList hunk)))

(defn entry-edits [repo entry]
  (let [formatter (doto (DiffFormatter. org.eclipse.jgit.util.io.DisabledOutputStream/INSTANCE)
                    (.setRepository (.getRepository repo)))]
    (map edits (hunks formatter entry))))

(defn changes-stats
  ([commit] (changes-stats (:repo commit) (parent-commit (:raw commit)) (:raw commit)))
  ([repo parent-commit commit]
   (let [entries (diff-entries-between-commits repo parent-commit commit)]
     (for [entry entries
           [adds rets] (entry-edits repo entry)]
       {::path (.getOldPath entry)
        ::additions adds
        ::deletions rets})
     #_(zipmap (map #(.getOldPath %) entries)
         (mapcat #(entry-edits repo %) entries)))))

(comment
  (changes-stats c)
  )
