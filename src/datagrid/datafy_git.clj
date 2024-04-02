(ns datagrid.datafy-git
  (:require [clj-jgit.porcelain :as git]
            [clj-jgit.querying :as git2]
            [clojure.core.protocols :as ccp :refer [nav]]
            [clojure.datafy :refer [datafy]]))

(def ... `...)

(extend-protocol ccp/Datafiable
  org.eclipse.jgit.api.Git
  (datafy [^org.eclipse.jgit.api.Git repo]
    (-> {:status (git/git-status repo)
         :log ...}
      (with-meta
        {`ccp/nav (fn [x k v]
                    (case k
                      :log (->> (git/git-log repo)
                             (map (fn [m]
                                    (let [commit (:id m) ; dumb attr name
                                          ci (git2/commit-info repo commit)]
                                      (assoc m
                                        ::commit commit ; alias to better name
                                        ::commit-info ci
                                        ::commit-id (str (:id ci))
                                        ::commit-id-short (apply str (take 7 (:id ci))))))))
                      v))})))

  org.eclipse.jgit.revwalk.RevCommit
  (datafy [^org.eclipse.jgit.revwalk.RevCommit o]
    ; (qualify (namespace ::x) :raw)
    (-> (git2/commit-info (:repo o) o)
      (dissoc :raw :repo)
      (assoc :raw ... :repo ...)
      (with-meta
        {`ccp/nav (fn [x k v]
                    (case k
                      :raw x
                      :repo (:repo x)
                      v))}))))

(comment
  (def r (git/load-repo "./"))
  (git/git-status r)
  (type r)
  (datafy r)
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
  (type ci)
  (.getShortMessage c))
