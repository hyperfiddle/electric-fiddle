(ns dustingetz.nav-git-explorer
  "Git Time Machine browser. Shells out to `git` CLI to browse branches,
   commits, file trees, diffs, and blame. Navigate through history with
   cross-linked authors, commits, and files."
  (:require
   [dustingetz.nav-util.shell :as shell]
   [clojure.string :as str]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]]))

;; -- Records --

(defrecord GitBranch   [name current upstream ahead behind])
(defrecord GitCommit   [hash short-hash author-name author-email date subject
                         files-changed insertions deletions])
(defrecord GitFileChange [path status insertions deletions])
(defrecord GitBlameEntry [hash short-hash author line-start line-count summary])
(defrecord GitTag      [name hash date tagger subject])
(defrecord GitStash    [index message branch])
(defrecord CommitRef   [hash])
(defrecord BranchRef   [name])

;; -- Data functions --

(defn branches []
  (when-let [out (shell/sh-str "git" "for-each-ref" "--format=%(refname:short)%09%(HEAD)%09%(upstream:short)%09%(upstream:track)" "refs/heads/")]
    (->> (str/split-lines out)
      (remove str/blank?)
      (mapv (fn [line]
              (let [[name head upstream track] (str/split line #"\t" -1)
                    current? (= "*" (str/trim (or head "")))
                    [_ ahead behind] (when track
                                       (re-matches #".*?ahead (\d+).*?behind (\d+).*" track))
                    [_ ahead-only] (when (and track (not ahead))
                                     (re-matches #".*?ahead (\d+).*" track))
                    [_ behind-only] (when (and track (not behind))
                                      (re-matches #".*?behind (\d+).*" track))]
                (->GitBranch
                  (str/trim name)
                  current?
                  (when-not (str/blank? upstream) upstream)
                  (or ahead ahead-only)
                  (or behind behind-only))))))))

(defn git-log
  ([] (git-log "HEAD" 200))
  ([ref] (git-log ref 200))
  ([ref limit]
   (when-let [out (shell/sh-str "git" "log" (str ref) (str "--max-count=" limit)
                    "--format=%H%x09%h%x09%an%x09%ae%x09%ai%x09%s"
                    "--shortstat")]
     (let [lines (str/split-lines out)
           ;; Lines alternate: format line, blank, stat line, blank...
           ;; Actually --shortstat interleaves stat lines after each commit
           parsed (loop [remaining lines
                         commits []]
                    (if (empty? remaining)
                      commits
                      (let [line (first remaining)]
                        (if (str/includes? (or line "") "\t")
                          ;; This is a commit line
                          (let [[hash short-hash author-name author-email date subject]
                                (str/split line #"\t" 6)
                                ;; Look ahead for stat line
                                rest-lines (rest remaining)
                                stat-line (first (drop-while str/blank? rest-lines))
                                [_ files ins dels]
                                (when stat-line
                                  (re-matches #"\s*(\d+) files? changed(?:, (\d+) insertions?...)?(?:, (\d+) deletions?...)?" stat-line))
                                skip (if files
                                       (rest (drop-while str/blank? rest-lines))
                                       rest-lines)]
                            (recur skip
                              (conj commits
                                (->GitCommit hash short-hash author-name author-email
                                  date subject
                                  (some-> files parse-long)
                                  (some-> ins parse-long)
                                  (some-> dels parse-long)))))
                          ;; Skip blank/stat lines
                          (recur (rest remaining) commits)))))]
       parsed))))

(defn git-log-simple
  ([] (git-log-simple "HEAD" 200))
  ([ref] (git-log-simple ref 200))
  ([ref limit]
   (when-let [out (shell/sh-str "git" "log" (str ref) (str "--max-count=" limit)
                    "--format=%H%x09%h%x09%an%x09%ae%x09%ai%x09%s")]
     (->> (str/split-lines out)
       (remove str/blank?)
       (mapv (fn [line]
               (let [[hash short-hash author-name author-email date subject]
                     (str/split line #"\t" 6)]
                 (->GitCommit hash short-hash author-name author-email
                   date subject nil nil nil))))))))

(defn find-commit [hash]
  (when-let [out (shell/sh-str "git" "show" hash "--format=%H%x09%h%x09%an%x09%ae%x09%ai%x09%s" "--stat")]
    (let [lines (str/split-lines out)
          [hash short-hash author-name author-email date subject]
          (str/split (first lines) #"\t" 6)
          stat-lines (rest lines)
          summary-line (last stat-lines)
          [_ files ins dels]
          (when summary-line
            (re-matches #"\s*(\d+) files? changed(?:, (\d+) insertions?...)?(?:, (\d+) deletions?...)?" summary-line))]
      (->GitCommit hash short-hash author-name author-email date subject
        (some-> files parse-long)
        (some-> ins parse-long)
        (some-> dels parse-long)))))

(defn commit-files [hash]
  (when-let [out (shell/sh-str "git" "diff-tree" "--no-commit-id" "-r" "--numstat" hash)]
    (->> (str/split-lines out)
      (remove str/blank?)
      (mapv (fn [line]
              (let [[ins dels path] (str/split line #"\t" 3)]
                (->GitFileChange
                  path
                  (cond
                    (= ins "-") "binary"
                    (= "0" dels) "added"
                    (= "0" ins) "deleted"
                    :else "modified")
                  (when-not (= ins "-") (parse-long ins))
                  (when-not (= dels "-") (parse-long dels)))))))))

(defn find-branch [name]
  (when-let [out (shell/sh-str "git" "for-each-ref" (str "--format=%(refname:short)%09%(HEAD)%09%(upstream:short)%09%(upstream:track)")
                   (str "refs/heads/" name))]
    (when-not (str/blank? out)
      (let [[bname head upstream track] (str/split (str/trim out) #"\t" -1)]
        (->GitBranch bname (= "*" (str/trim (or head "")))
          (when-not (str/blank? upstream) upstream) nil nil)))))

(defn tags []
  (when-let [out (shell/sh-str "git" "tag" "-l" "--format=%(refname:short)%x09%(objectname:short)%x09%(creatordate:iso8601)%x09%(taggername)%x09%(subject)")]
    (->> (str/split-lines out)
      (remove str/blank?)
      (mapv (fn [line]
              (let [[name hash date tagger subject] (str/split line #"\t" 5)]
                (->GitTag name hash date tagger subject)))))))

(defn stashes []
  (when-let [out (shell/sh-str "git" "stash" "list" "--format=%gd%x09%gs%x09%s")]
    (->> (str/split-lines out)
      (remove str/blank?)
      (mapv (fn [line]
              (let [[index message subject] (str/split line #"\t" 3)]
                (->GitStash index (or message subject) nil)))))))

(defn branch-log [branch-name]
  (git-log-simple branch-name 100))

;; -- Named accessors --

(defn commit-short-hash [c] (:short-hash c))
(defn commit-short [c] (->CommitRef (:hash c)))
(defn commit-author [c] (:author-name c))
(defn branch-name-fn [b] (->BranchRef (:name b)))
(defn tag-name [t] (:name t))
(defn tag-commit [t] (->CommitRef (:hash t)))
(defn file-path [f] (:path f))

;; -- Protocol extensions --

(extend-type CommitRef
  Identifiable (-identify [r] `(find-commit ~(:hash r)))
  Suggestable (-suggest [_] (hfql [:hash])))

(extend-type BranchRef
  Identifiable (-identify [r] `(find-branch ~(:name r)))
  Suggestable (-suggest [_] (hfql [:name])))

(extend-type GitBranch
  Identifiable (-identify [b] `(find-branch ~(:name b)))
  Suggestable (-suggest [_] (hfql [:name :current :upstream :ahead :behind])))

(extend-type GitCommit
  Identifiable (-identify [c] `(find-commit ~(:hash c)))
  Suggestable (-suggest [_] (hfql [:short-hash :author-name :date :subject
                                    :files-changed :insertions :deletions])))

(extend-type GitFileChange
  Identifiable (-identify [f] `(find-commit "HEAD")) ; files don't have standalone identity
  Suggestable (-suggest [_] (hfql [:path :status :insertions :deletions])))

(extend-type GitTag
  Identifiable (-identify [t] `(find-commit ~(:hash t)))
  Suggestable (-suggest [_] (hfql [:name :hash :date :tagger :subject])))

(extend-type GitStash
  Identifiable (-identify [s] `(find-commit ~(:index s)))
  Suggestable (-suggest [_] (hfql [:index :message :branch])))

;; -- Sitemap --

(def sitemap
  {`branches
   (hfql {(branches) {* ^{::hfql/select `find-branch}
                        [^{::hfql/link `(find-branch ~'%)} branch-name-fn
                         :current :upstream]}})

   `git-log-simple
   (hfql {(git-log-simple) {* ^{::hfql/select `find-commit}
                              [^{::hfql/link `(find-commit ~'%)} commit-short-hash
                               :author-name :date :subject]}})

   `find-commit
   (hfql [:short-hash :author-name :author-email :date :subject
          :files-changed :insertions :deletions])

   `find-branch
   (hfql [:name :current :upstream :ahead :behind])

   `branch-log
   (hfql {(branch-log %) {* ^{::hfql/select `find-commit}
                            [^{::hfql/link `(find-commit ~'%)} commit-short-hash
                             :author-name :date :subject]}})

   `commit-files
   (hfql {(commit-files %) {* [file-path :status :insertions :deletions]}})

   `tags
   (hfql {(tags) {* ^{::hfql/select `find-commit}
                    [tag-name ^{::hfql/link `(find-commit ~'%)} tag-commit
                     :date :subject]}})

   `stashes
   (hfql {(stashes) {* [:index :message]}})})

;; -- Resolvers --

(defmethod -hfql-resolve `branches [_] (branches))
(defmethod -hfql-resolve `git-log-simple [_] (git-log-simple))
(defmethod -hfql-resolve `find-commit [[_ hash]] (find-commit hash))
(defmethod -hfql-resolve `find-branch [[_ name]] (find-branch name))
(defmethod -hfql-resolve `branch-log [[_ branch-name]] (branch-log branch-name))
(defmethod -hfql-resolve `commit-files [[_ hash]] (commit-files hash))
(defmethod -hfql-resolve `tags [_] (tags))
(defmethod -hfql-resolve `stashes [_] (stashes))
