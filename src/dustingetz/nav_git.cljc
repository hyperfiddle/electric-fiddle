(ns ^{:hyperfiddle.electric.impl.lang3/has-edef? true} ; enable server hot reloading
  dustingetz.nav-git
  #?(:clj (:import (org.eclipse.jgit.api Git)
                   (org.eclipse.jgit.internal.storage.file FileRepository)
                   (org.eclipse.jgit.revwalk RevCommit)
                   (org.eclipse.jgit.lib Constants ObjectId ObjectIdRef Ref Repository PersonIdent)))
  (:require
    #?(:clj [clj-jgit.porcelain :as git])
    #?(:clj clj-jgit.util)
    [hyperfiddle.hfql0 #?(:clj :as :cljs :as-alias) hfql]))

#?(:clj
   (do

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
     (defn repo-path [^Git x] (-> x .getRepository .getDirectory #_fs/file-absolute-path))
     (defn repo-repo [^Git x] (-> x .getRepository))
     (defn repo-status [^Git x] (git/git-status x))

     (def branch-current (memoize (fn [^Git x] (git/git-branch-current x))))
     (def branch-list (memoize (fn [^Git x] (vec (git/git-branch-list x :jgit? true :list-mode :all))))) ; arraylist
     (defn log [^Git x] (vec (git/git-log x :until "HEAD" :jgit? true)))

     (defn commit-name [^RevCommit o] (.getName o))
     (defn commit-short-name [^RevCommit o] (-> o .getName short-commit-id))
     (defn commit-message [^RevCommit o] (.getShortMessage o))
     (defn commit-author-ident [^RevCommit o] (.getAuthorIdent o))
     (defn commit-committer-ident [^RevCommit o] (.getCommitterIdent o))

     (comment
       (->> (load-repo "./") repo-path (fs/relativize-path (fs/absolute-path "./"))) := ".git")

     (defn file-exists? [path] (.exists (clojure.java.io/file path)))
     (def git-repo-path (first (filter file-exists? ["./.git" "../.git"])))
     (defn load-project-git-repo [] (load-repo git-repo-path))

     ;; preload to optimize page load in prod
     (try (load-project-git-repo) ; memoized
          (catch Throwable _))

     ;index: `(load-repo ~git-repo-path)

     (def sitemap
       (hfql/sitemap
         {load-project-git-repo []}))

     (extend-protocol hfql/Identifiable
       Git (-identify [^Git x] (repo-id x))
       FileRepository (-identify [^FileRepository x] (.getIdentifier x))
       Ref (-identify [^Ref x] (Repository/shortenRefName (.getName x)))
       ObjectId (-identify [^ObjectId x] (.getName x))
       RevCommit (-identify [^RevCommit x] (commit-short-name x))
       PersonIdent (-identify [^PersonIdent x] (.getEmailAddress x)))

     (comment
       (require '[hyperfiddle.hfql0 :refer [identify]])
       (def x (load-repo "./"))
       (identify x) := "./.git"

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

     (extend-protocol hfql/Suggestable
       Git
       (-suggest [_]
         (hfql/pull-spec [.getRepository
                          git/git-status
                          git/git-branch-current
                          branch-list
                          log]))
       RevCommit
       (-suggest [_]
         (hfql/pull-spec [#_.getName
                          commit-short-name
                          .getShortMessage
                          {.getAuthorIdent .getName}
                          #_{.getCommitterIdent .getName}]))
       PersonIdent
       (-suggest [_]
         (hfql/pull-spec [.getName
                          .getEmailAddress
                          .getWhen
                          .getTimeZone]))
       Ref
       (-suggest [_]
         (hfql/pull-spec [ref-short-name
                          #_ref-commit
                          ref-commit-short
                          ref-type
                          #_.getObjectId]))
       ObjectId
       (-suggest [_]
         (hfql/pull-spec [str .getName])))

     ))