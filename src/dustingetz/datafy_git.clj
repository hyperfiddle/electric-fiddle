(ns dustingetz.datafy-git
  (:require [clj-jgit.porcelain :as git]
            [clj-jgit.querying :as git2]
            [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]]
            [dustingetz.datafy-fs :as fs])
  (:import (org.eclipse.jgit.api Git)
           (org.eclipse.jgit.diff DiffFormatter DiffEntry RawTextComparator)
           (org.eclipse.jgit.internal.storage.file FileRepository)
           (org.eclipse.jgit.revwalk RevWalk RevCommit RevCommitList)
           (org.eclipse.jgit.lib Constants ObjectId ObjectIdRef ObjectIdRef$PeeledNonTag Ref Repository)))

; re-export wrappers for convenience - one API not two
(defn load-repo [path] (git/load-repo path))
(defn log [repo & args] (apply git/git-log repo args))

(declare get-commit changes-stats)

(defn resolve-ref [ref]
  (if (.isSymbolic ref)
    (resolve-ref (.getTarget ref))
    ref))

(defn branch-list [repo]
  (update-vals (clj-jgit.internal/get-refs repo "") resolve-ref))

(comment
  (def r (git/load-repo "./"))
  (log r)
  (type r) := Git

  (keys (first (git/log r))) := [:id :msg :author :committer]
  (def m (datafy r))
  (keys (first (nav m :log (:log m)))) :=
  [:email :raw :time :branches :changed_files :merge :author :id :repo :message]

  (def repo (.getRepository r))
  (type repo) := FileRepository
  (.getDirectory repo) := _ ; #object[java.io.File 0xa4ef80e "./.git"]


  (branch-list r) := { "refs/remotes/origin/release/electric-fiddle-v3" _
                      ... ...}
  (resolve-ref _ref)
  (get-commit r "refs/remotes/origin/main")
  (git/git-branch-list r :jgit? false :list-mode :all)
  )

(defn find-branches [branches commit]
  (map first
    (filter (fn [[_branch ref]]
              (= (.name (.getObjectId ref)) (:id commit)))
      branches)))

(comment
  (find-branches (branch-list r) (first (nav (datafy r) :log ())))
  (datafy (first (nav (datafy r) :log ())))
  (clj-jgit.internal/get-refs r "")
  (.name (.getObjectId (val (first (branch-list r)))))
  )

(defn with-datafy
  ([commit] (with-datafy (:repo commit) commit))
  ([repo commit] (with-datafy repo (branch-list repo) commit))
  ([_repo branches commit]
   (with-meta commit {`ccp/datafy (fn datafy [commit]
                                    (let [original (:clojure.datafy/obj (meta commit) commit)
                                          branches (find-branches branches original)]
                                      (assoc commit ::branches branches
                                        ::changes (changes-stats original))))})))

(comment
  (-> r log first :id .getName)
  (datafy RevCommit)


  #_(log (.getRepository r)) ; wrong type
  )

(defn short-commit-id [id] (apply str (take 7 id)))

(defn better-log [o branch]
  (let [rev-walk (clj-jgit.internal/new-rev-walk o)
        index    (git2/build-commit-map o rev-walk) ; pre-compute for fast ref resolve
        branches (branch-list o)]
    (->> (git/git-log o :until branch)
      #_(map (fn [commit]
             (with-datafy o branches
               (git2/commit-info o rev-walk index (:id commit))))))))

(defn repo-path [o]
  (-> o datafy :repo datafy :dir datafy ::fs/absolute-path))

#_(defn remote-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_REMOTES))
#_(defn local-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_HEADS))
(defn ref-type [^Ref ref]
  (if (.startsWith (.getName ref) "refs/remotes/") :remote :local))

(defn ref-name-short [^Ref ref]
  )

(comment
  (datafy r)
  (-> r datafy :repo datafy :dir datafy ::fs/absolute-path)
  (as-> r o
    (datafy o)
    (nav o :log (:log o))
    (first o))
  )

(extend-protocol ccp/Datafiable
  Git
  (datafy [^Git o]
    (-> {:status (git/git-status o)
         :repo (.getRepository o)
         :branch-current (git/git-branch-current o)
         ;:branches #(vec (branch-list o)) ; crashes
         :branches #(vec (git/git-branch-list o ; arraylist
                           :jgit? true :list-mode :all))
         :log #(vec (better-log o "HEAD"))}
      #_(with-meta
        {`ccp/nav (fn rec [x k v
                           & {:keys [branch]
                              :or {branch "HEAD"}}]
                    (cond
                      (vector? k)
                      (let [[command & args] k]
                        (apply rec x command v args))
                      (keyword? k)
                      (case k
                        :log (better-log o branch)
                        :branches (vec (branch-list o))
                        :branches2 (vec (git/git-branch-list o ; arraylist
                                          :jgit? true
                                          :list-mode :all))
                        v)))})))
  FileRepository
  (datafy [^FileRepository o]
    (-> {:dir (.getDirectory o)}
      (with-meta
        {`ccp/nav
         (fn [x k v & {:as kwargs}]
           )})))

  Ref #_ObjectIdRef ; including ObjectIdRef$PeeledNonTag
  (datafy [^ObjectIdRef o]
    {:ref-name (.getName o) ; refs/remotes/origin/agent-network, refs/heads/wip/hot-md
     :ref-name-short (Repository/shortenRefName (.getName o))
     :commit (-> o .getObjectId .getName) ; "88a97ef20b1fe5392a6025eca4170c80ad3479ce"
     :commit-short (-> o .getObjectId .getName short-commit-id)
     :symbolic (-> o .isSymbolic)
     :peeled (-> o .isPeeled)
     :ref-type (-> o ref-type)
     :object-id (.getObjectId o)}) ; #object[org.eclipse.jgit.lib.ObjectId

  ObjectId
  (datafy [^ObjectId o]
    {::toString (.toString o)})

  RevCommit
  (datafy [^RevCommit o]
    {:name (.getName o)
     :short-name (-> o .getName short-commit-id)}))

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

(comment
  (as-> o x
    (datafy x)
    (nav x :branches2 (:branches2 x))
    (nav x 0 (x 0))
    (datafy x)
    #_(nav x :name (:name x))
    #_(nav x :object-id (:object-id x)) #_(datafy x))
  )

(comment
  (def o (git/load-repo "./"))
  (git/git-status r)
  (type r)
  (datafy r)
  (count (nav (datafy r) :log ()))
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
  ([commit index] (when (< index (.getParentCount commit))
                    (.getParent commit index))))

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
            [(+ adds (.getLengthB edit))
             (+ rets (.getLengthA edit))])
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
        ::deletions rets}))))

(comment
  (changes-stats c)
  )


(comment
  ;; (git/git-checkout r :name "refs/heads/main")
  (git/git-log r :until "HEAD" #_"refs/heads/main")
  )
