(ns dustingetz.nav-maven
  "Maven Central artifact search browser."
  (:require
   [clojure.string :as str]
   [dustingetz.nav-util.http :as http]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]])
  (:import
   [java.net URI URLEncoder]))

;; -- Records --

(defrecord MavenArtifact [group-id artifact-id latest-version timestamp version-count])
(defrecord ArtifactRef [group-id artifact-id])

;; -- Data functions --

(defn search-maven
  "Search Maven Central for artifacts matching a query string."
  [query]
  (when-let [data (http/fetch-json
                    (str "https://search.maven.org/solrsearch/select?q="
                      (URLEncoder/encode query "UTF-8") "&rows=20&wt=json"))]
    (mapv (fn [d]
            (->MavenArtifact (:g d) (:a d) (:latestVersion d)
              (:timestamp d) (:versionCount d)))
      (get-in data [:response :docs]))))

(defn find-artifact
  "Single Maven Central artifact by group-id and artifact-id."
  [group-id artifact-id]
  (when-let [data (http/fetch-json
                    (str "https://search.maven.org/solrsearch/select?q=g:"
                      (URLEncoder/encode group-id "UTF-8") "+AND+a:"
                      (URLEncoder/encode artifact-id "UTF-8") "&rows=1&wt=json"))]
    (let [d (first (get-in data [:response :docs]))]
      (when d
        (->MavenArtifact (:g d) (:a d) (:latestVersion d)
          (:timestamp d) (:versionCount d))))))

;; -- Named accessors --

(defn artifact-coord [a] (str (:group-id a) "/" (:artifact-id a)))

;; -- Protocol extensions --

(extend-type ArtifactRef
  Identifiable (-identify [r] `(find-artifact ~(:group-id r) ~(:artifact-id r)))
  Suggestable (-suggest [_] (hfql [:group-id :artifact-id])))

(extend-type MavenArtifact
  Identifiable (-identify [a] `(find-artifact ~(:group-id a) ~(:artifact-id a)))
  Suggestable (-suggest [_] (hfql [:group-id :artifact-id :latest-version :timestamp :version-count])))

;; -- Sitemap --

(def sitemap
  {`search-maven
   (hfql {(search-maven %) {* ^{::hfql/select `find-artifact}
                               [artifact-coord :latest-version :version-count]}})

   `find-artifact
   (hfql [:group-id :artifact-id :latest-version :timestamp :version-count])})

;; -- Resolvers --

(defmethod -hfql-resolve `search-maven [[_ query]] (search-maven query))
(defmethod -hfql-resolve `find-artifact [[_ gid aid]] (find-artifact gid aid))
