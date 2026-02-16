(ns dustingetz.nav-git
  (:import
   (org.eclipse.jgit.api Git)
   (org.eclipse.jgit.internal.storage.file FileRepository)
   (org.eclipse.jgit.revwalk RevCommit)
   (org.eclipse.jgit.lib Constants ObjectId ObjectIdRef Ref Repository PersonIdent))
  (:require
   [clj-jgit.porcelain :as git]
   [clj-jgit.querying :refer [find-rev-commit]]
   [clj-jgit.internal :refer [new-rev-walk]]
   clj-jgit.util
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :as hfqlp]))

(def ^:dynamic *repo-path*) ; DI

; re-export wrappers for convenience - one API not two
(def load-repo (memoize git/load-repo))
#_(def log git/git-log)
#_(def branch-list git/git-branch-list)

(defn short-commit-id [id] (apply str (take 7 id)))

#_(defn remote-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_REMOTES))
#_(defn local-ref? [^Ref ref] (.startsWith (.getName ref) Constants/R_HEADS))
(defn ref-type [^Ref ref] (if (.startsWith (.getName ref) "refs/remotes/") :remote :local))
(defn ref-short-name [^Ref o] (Repository/shortenRefName (.getName o)))
(defn ref-commit [^Ref o] (-> o .getObjectId .getName))
(defn ref-commit-short [^Ref o] (-> o .getObjectId .getName short-commit-id))

(defn repo-id [^Git x] (-> x .getRepository .getIdentifier))
(defn repo-path [^Git x] (-> x .getRepository .getWorkTree #_fs/file-absolute-path))
(defn repo-repo [^Git x] (-> x .getRepository))
(defn repo-status [^Git x] (git/git-status x))

(def branch-current (memoize (fn [^Git x] (git/git-branch-current x))))
(def branch-list (memoize (fn [^Git x] (vec (git/git-branch-list x :jgit? true :list-mode :all))))) ; arraylist
(defn log [^Git repo] (vec (git/git-log repo :until "HEAD" :jgit? true)))
(defn ref-log [^Ref ref] (vec (git/git-log (load-repo *repo-path*) :until (.getName ref) :jgit? true)))

(defn commit-name [^RevCommit o] (.getName o))
(defn commit-short-name [^RevCommit o] (-> o .getName short-commit-id))
(defn commit-message [^RevCommit o] (.getShortMessage o))
(defn commit-author-ident [^RevCommit o] (.getAuthorIdent o))
(defn commit-committer-ident [^RevCommit o] (.getCommitterIdent o))

(defn rev-commit [repo commit-short-name-str] (find-rev-commit repo (new-rev-walk repo) commit-short-name-str))

(defn find-ref ^Ref [^Repository repo ^String ref-name-str]
  (let [refdb (.getRefDatabase repo)]
    (some (fn [^String full-ref-name-str]
            (let [^Ref r (.exactRef refdb full-ref-name-str)]
              (when (and r (or (.startsWith (.getName r) Constants/R_HEADS)
                             (.startsWith (.getName r) Constants/R_REMOTES)))
                r)))
      ;; try local first, then remote-tracking, then (if already fully qualified) as-is
      (if (.startsWith ref-name-str "refs/")
        [ref-name-str]
        [(str Constants/R_HEADS ref-name-str)
         (str Constants/R_REMOTES ref-name-str)]))))

(comment
  (->> (load-repo "./") repo-path (fs/relativize-path (fs/absolute-path "./"))) := ".git")

(defn file-exists? [path] (.exists (clojure.java.io/file path)))
(def git-repo-path (first (filter file-exists? ["./.git" "../.git"])))
(defn load-project-git-repo [] (load-repo git-repo-path))

;; preload to optimize page load in prod
(try (load-project-git-repo) ; memoized
  (catch Throwable _))

(comment
  (require '[hyperfiddle.hfql2 :refer [identify]])
  (def x (load-repo "../"))
  (identify x) := "./.git"

  (branch-list x)

  (as-> (datafy x) x
    (datafy x) (nav x :repo (:repo x))
    (identify x)) := "./.git"

  (as-> (datafy x) x
    (datafy x) (nav x :repo (:repo x))
    (identify x)) := "./.git"

  (as-> (datafy x) x
    (datafy x) (nav x :log (:log x))
    (datafy x) (nav x 0 (nth x 0)) ; log record
    (identify x)) := "b10bf19"

  (as-> (datafy x) x
    (datafy x) (nav x :log (:log x))
    (datafy x) (nav x 0 (nth x 0)) ; log record
    (datafy x) (nav x :id (:id x))
    (identify x)) := "47f2ce3"

  (as-> (datafy x) x
    (datafy x) (nav x :branches (:branches x))
    (datafy x) (nav x 0 (nth x 0)) ; log record
    (identify x)) := "agent-network"

  (as-> (datafy x) x
    (datafy x) (nav x :branches (:branches x))
    (datafy x) (nav x 0 (nth x 0)) ; log record
    (datafy x) (nav x :object-id (:object-id x))
    (identify x)) := "12b7acf4d68519b8fa98a31828b5f725abaf80e0")

(extend-type Git
  hfqlp/Identifiable (-identify [^Git x] `(load-repo ~(str (repo-path x))))
  hfqlp/Suggestable (-suggest [_] (hfql [.getRepository])))

(defmethod hfqlp/-hfql-resolve `load-repo [[_ worktree-path-str]] (load-repo worktree-path-str))

(extend-type RevCommit
  hfqlp/Identifiable (-identify [x] `(rev-commit ~(commit-short-name x)))
  hfqlp/Suggestable (-suggest [_] (hfql [commit-short-name
                                         .getShortMessage
                                         {.getAuthorIdent .getName}])))

(defmethod hfqlp/-hfql-resolve `rev-commit [[_ commit-short-name-str]] (rev-commit (load-repo *repo-path*) commit-short-name-str))

(extend-type Ref
  hfqlp/Identifiable (-identify [^Ref x] `(find-ref ~(Repository/shortenRefName (.getName x))))
  hfqlp/Suggestable (-suggest [_] (hfql [ref-short-name ref-commit-short ref-type])))

(defmethod hfqlp/-hfql-resolve `find-ref [[_ ref-short-name-str]] (find-ref (repo-repo (load-repo *repo-path*)) ref-short-name-str))

(extend-type PersonIdent ; not identifiable
  hfqlp/Suggestable (-suggest [_] (hfql [.getName
                                         .getEmailAddress
                                         .getWhen
                                         .getTimeZone])))

