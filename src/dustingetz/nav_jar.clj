(ns dustingetz.nav-jar
  (:import [java.util.jar JarFile JarEntry])
  (:require
    [dustingetz.fs2 :refer [path-filename]]
    [hyperfiddle.hfql0 :as hfql]))

(defn list-project-jars []
  (let [cp (System/getProperty "java.class.path")]
    (->> (clojure.string/split cp #"[;:]")
      (filter #(.endsWith % ".jar"))
      (map #(JarFile. (clojure.java.io/file %))))))

(defn jar-entries [!jar] (enumeration-seq (.entries !jar)))
(defn jar-manifest [!jar] (some->> !jar .getManifest .getMainAttributes .entrySet seq (into {})))
(defn jar-filename [!jar] (path-filename (.getName !jar)))
(defn jar-jmanifest-entries [!jar-manifest] (into {} (seq (.entrySet !jar-manifest))))

(extend-protocol hfql/Suggestable
  JarFile (-suggest [_] (hfql/pull-spec [.getName jar-filename jar-manifest .getVersion jar-entries type]))
  java.util.jar.Manifest (-suggest [_] (hfql/pull-spec [.getMainAttributes #_.getEntries type]))
  java.util.jar.JarFile$JarFileEntry (-suggest [_] (hfql/pull-spec [.getName .getSize type]))
  java.util.jar.Attributes (-suggest [_] (hfql/pull-spec [.values jar-jmanifest-entries type])))