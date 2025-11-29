(ns dustingetz.nav-jar
  (:import [java.util.jar JarFile JarEntry])
  (:require
    [dustingetz.fs2 :refer [path-filename]]
    [hyperfiddle.hfql2 :as hfql :refer [hfql]]
    [hyperfiddle.hfql2.protocols :as hfqlp]))

(defn list-project-jars []
  (let [cp (System/getProperty "java.class.path")]
    (->> (clojure.string/split cp #"[;:]")
      (filter #(.endsWith % ".jar"))
      (map #(JarFile. (clojure.java.io/file %))))))

(defn jar-entries [!jar] (enumeration-seq (.entries !jar)))
(defn jar-manifest [!jar] (some->> !jar .getManifest .getMainAttributes .entrySet seq (into {})))
(defn jar-filename [!jar] (path-filename (.getName !jar)))
(defn jar-jmanifest-entries [!jar-manifest] (into {} (seq (.entrySet !jar-manifest))))

(extend-type java.util.jar.JarFile$JarFileEntry
  hfqlp/Identifiable (identify [x] (-> x str))
  hfqlp/Suggestable (suggest [_] (hfql [.getName
                                        .getSize
                                        .getCompressedSize
                                        type])))

(extend-type java.util.jar.JarFile
  hfqlp/Identifiable (identify [x] `(jar-by-path ~(.getName x)))
  hfqlp/Suggestable (suggest [_] (hfql [.getName
                                        jar-filename
                                        jar-manifest
                                        .getVersion
                                        jar-entries
                                        type])))

(extend-type java.util.jar.Manifest
  hfqlp/Suggestable (suggest [_] (hfql [.getMainAttributes #_.getEntries type])))

(extend-type java.util.jar.JarFile$JarFileEntry
  hfqlp/Suggestable (suggest [_] (hfql [.getName .getSize type])))

(extend-type java.util.jar.Attributes
  hfqlp/Suggestable (suggest [_] (hfql [.values jar-jmanifest-entries type])))

;; TODO: Crashes
;; #?(:clj (extend-type java.util.jar.Attributes$Name
;;           hfqlp/Identifiable (identify [x] (str x))))

(extend-type java.util.jar.Attributes$Name
  hfqlp/Suggestable (suggest [_] (hfql [.toString])))

(defmethod hfqlp/hfql-resolve 'dustingetz.nav-jar/jar-by-path [[_ jar-path]]
  (JarFile. (clojure.java.io/file jar-path)))
